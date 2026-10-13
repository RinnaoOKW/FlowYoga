package com.flowyoga.member.management;
import com.flowyoga.management.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/member/admin/members")
public class MemberManagementController extends DirectoryController {
    public MemberManagementController(DirectoryService service) { super(service); }
}
