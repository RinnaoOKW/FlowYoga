package com.flowyoga.course.management;
import com.flowyoga.management.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/course/admin/coaches")
public class CoachManagementController extends DirectoryController {
    public CoachManagementController(DirectoryService service) { super(service); }
}
