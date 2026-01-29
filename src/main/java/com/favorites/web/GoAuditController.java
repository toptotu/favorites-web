package com.favorites.web;

import com.favorites.comm.aop.LoggerManage;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class GoAuditController extends BaseController {

    @RequestMapping("/go-audit")
    @LoggerManage(description = "Go代码审计入口")
    public String goAudit() {
        return "go-audit";
    }
}
