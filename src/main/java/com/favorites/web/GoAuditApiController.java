package com.favorites.web;

import com.favorites.audit.GoAuditRequest;
import com.favorites.audit.GoAuditResult;
import com.favorites.audit.GoAuditService;
import com.favorites.comm.aop.LoggerManage;
import com.favorites.domain.result.ExceptionMsg;
import com.favorites.domain.result.ResponseData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/go-audit")
public class GoAuditApiController extends BaseController {

    @Autowired
    private GoAuditService goAuditService;

    @RequestMapping(value = "/scan", method = RequestMethod.POST)
    @LoggerManage(description = "Go代码审计扫描")
    public ResponseData scan(@RequestBody GoAuditRequest request) {
        try {
            GoAuditResult result = goAuditService.scan(request);
            return new ResponseData(ExceptionMsg.SUCCESS, result);
        } catch (IllegalArgumentException ex) {
            return new ResponseData(ExceptionMsg.ParamError, ex.getMessage());
        } catch (Exception ex) {
            return new ResponseData(ExceptionMsg.FAILED, ex.getMessage());
        }
    }
}
