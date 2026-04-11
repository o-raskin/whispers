package com.oraskin.resource;

import com.oraskin.common.mvc.annotation.RequestMapping;
import com.oraskin.common.mvc.annotation.RequestParam;
import com.oraskin.common.mvc.annotation.RestController;
import com.oraskin.user.session.service.SessionService;

import java.io.OutputStream;
import java.net.Socket;

@RestController("/ws/user")
public final class UserConnectionController {

    private final SessionService sessionService;

    public UserConnectionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @RequestMapping(method = "GET")
    public String connect(
            @RequestParam("userId") String userId,
            Socket socket,
            OutputStream output
    ) {
        sessionService.openSession(userId, socket, output);
        return "CONNECTED:" + userId;
    }
}
