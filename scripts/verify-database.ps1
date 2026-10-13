param([string]$MySqlHome = 'E:\MySQL Main')
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
. "$PSScriptRoot\stage2-runtime.ps1"
$settings = Read-FlowYogaLocalSettings $root
$mysql = Join-Path $MySqlHome 'bin\mysql.exe'
$script:checks = 0

# 每项写入均在单独连接的事务中；成功显式回滚，失败由连接关闭回滚。
function Test-DatabaseSql {
    param([string]$Name, [string]$Owner, [string]$Sql, [int]$ExpectedError = 0, [string]$ExpectedOutput = 'PASS')
    $previousPassword = $env:MYSQL_PWD
    $previousEncoding = $OutputEncoding
    $previousPreference = $ErrorActionPreference
    try {
        $env:MYSQL_PWD = $settings["flowyoga.database.$Owner-password"]
        $OutputEncoding = New-Object System.Text.UTF8Encoding($false)
        $ErrorActionPreference = 'Continue'
        $result = $Sql | & $mysql --no-defaults --protocol=TCP --host=127.0.0.1 --port=13306 "--user=flowyoga_$Owner" --default-character-set=utf8mb4 --batch --skip-column-names --connect-timeout=3 2>&1
        $exitCode = $LASTEXITCODE
        $rendered = ($result | ForEach-Object { $_.ToString() }) -join "`n"
        if ($ExpectedError) {
            if ($exitCode -eq 0 -or $rendered -notmatch "ERROR $ExpectedError\b") { throw "$Name 未按预期拒绝；$rendered" }
        } elseif ($exitCode -ne 0 -or $rendered.Trim() -ne $ExpectedOutput) {
            throw "$Name 失败；$rendered"
        }
        $script:checks++
        Write-Host "通过：$Name"
    } finally {
        $env:MYSQL_PWD = $previousPassword
        $OutputEncoding = $previousEncoding
        $ErrorActionPreference = $previousPreference
    }
}

foreach ($entry in @(@('user',6), @('member',5), @('course',7))) {
    $owner = $entry[0]
    Test-DatabaseSql "$owner 库业务表数量" $owner "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='flowyoga_$owner' AND table_name <> 'flyway_schema_history';" -ExpectedOutput "$($entry[1])"
}
Test-DatabaseSql '本地演示账号使用 BCrypt' 'user' "SELECT COUNT(*) FROM flowyoga_user.sys_user WHERE login_name IN ('admin','coach','member') AND LEFT(password_hash,4) IN ('`$2a`$','`$2b`$','`$2y`$') AND CHAR_LENGTH(password_hash)=60;" -ExpectedOutput '3'
foreach ($owner in @('user','member','course')) {
    $other = if ($owner -eq 'user') { 'member.member_profile' } else { 'user.sys_user' }
    Test-DatabaseSql "$owner 账号不能读取其他服务库" $owner "SELECT * FROM flowyoga_$other LIMIT 0;" -ExpectedError 1142
}
Test-DatabaseSql '登录名唯一' 'user' @'
START TRANSACTION;
INSERT INTO flowyoga_user.sys_user (id,login_name,password_hash,display_name,phone,role_code)
SELECT -810001,login_name,password_hash,'constraint test','qa-phone-login','ADMIN' FROM flowyoga_user.sys_user WHERE login_name='admin';
ROLLBACK;
'@ -ExpectedError 1062
Test-DatabaseSql '手机号唯一' 'user' @'
START TRANSACTION;
INSERT INTO flowyoga_user.sys_user (id,login_name,password_hash,display_name,phone,role_code)
SELECT -810001,'qa-phone-test',password_hash,'constraint test',phone,'ADMIN' FROM flowyoga_user.sys_user WHERE login_name='admin';
ROLLBACK;
'@ -ExpectedError 1062
Test-DatabaseSql '负金额被拒绝' 'member' @'
START TRANSACTION;
INSERT INTO flowyoga_member.card_plan (id,name,session_count,valid_days,list_price) VALUES (-810001,'constraint test',10,90,-1);
ROLLBACK;
'@ -ExpectedError 3819
$cardFixture = @'
USE flowyoga_member;
START TRANSACTION;
INSERT INTO member_profile (id,user_id) VALUES (-810001,-810001);
INSERT INTO card_plan (id,name,session_count,valid_days,list_price) VALUES (-810001,'constraint test',10,90,100);
'@
Test-DatabaseSql '实收与标价不同时必须说明原因' 'member' ($cardFixture + @'

INSERT INTO member_card (id,member_id,plan_id,plan_name_snapshot,list_price_snapshot,valid_days_snapshot,total_sessions,remaining_sessions,actual_paid_amount,paid_at,issued_by,issued_at,expires_at)
VALUES (-810001,-810001,-810001,'constraint test',100,90,10,10,80,UTC_TIMESTAMP(),1001,UTC_TIMESTAMP(),DATE_ADD(UTC_TIMESTAMP(),INTERVAL 90 DAY));
ROLLBACK;
'@) -ExpectedError 3819
Test-DatabaseSql '释放先到可以封存无卡流水' 'member' @'
START TRANSACTION;
INSERT INTO flowyoga_member.card_session_ledger (booking_id,member_id,confirmation_deadline,state) VALUES ('qa-release-first',-810001,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 120 SECOND),'RELEASED');
SELECT 'PASS';
ROLLBACK;
'@
Test-DatabaseSql '占用流水必须关联实际会员卡' 'member' @'
START TRANSACTION;
INSERT INTO flowyoga_member.card_session_ledger (booking_id,member_id,confirmation_deadline,state) VALUES ('qa-hold-no-card',-810001,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 120 SECOND),'HELD');
ROLLBACK;
'@ -ExpectedError 3819
$courseFixture = @'
USE flowyoga_course;
START TRANSACTION;
INSERT INTO coach_profile (id,user_id) VALUES (-810001,-810001);
INSERT INTO yoga_course (id,name,difficulty,default_duration_minutes) VALUES (-810001,'constraint test','BEGINNER',60);
INSERT INTO studio_room (id,name,capacity) VALUES (-810001,'qa-constraint-room',10);
INSERT INTO class_session (id,course_id,coach_id,room_id,starts_at,ends_at,seat_limit) VALUES (-810001,-810001,-810001,-810001,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 DAY),DATE_ADD(UTC_TIMESTAMP(),INTERVAL 25 HOUR),10);
INSERT INTO course_booking (id,class_session_id,member_id,confirmation_deadline,created_at) VALUES ('qa-booking-first',-810001,-810001,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 120 SECOND),UTC_TIMESTAMP());
'@
$secondBooking = "INSERT INTO course_booking (id,class_session_id,member_id,confirmation_deadline,created_at) VALUES ('qa-booking-second',-810001,-810001,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 120 SECOND),UTC_TIMESTAMP());"
Test-DatabaseSql '同一课次的有效预约不可重复' 'course' ($courseFixture + "`n$secondBooking`nROLLBACK;") -ExpectedError 1062
Test-DatabaseSql '取消后允许保留历史并再次预约' 'course' ($courseFixture + "`nUPDATE course_booking SET status='CANCELLED' WHERE id='qa-booking-first';`n$secondBooking`nSELECT IF(COUNT(*)=2 AND SUM(active_member_id IS NOT NULL)=1,'PASS','FAIL') FROM course_booking WHERE class_session_id=-810001;`nROLLBACK;")
Test-DatabaseSql '课次不能超出名额' 'course' ($courseFixture + "`nUPDATE class_session SET occupied_seats=11 WHERE id=-810001;`nROLLBACK;") -ExpectedError 3819
Write-Host "MySQL 验证通过，共 $script:checks 项。测试业务数据已回滚。"
