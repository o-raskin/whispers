package com.oraskin.auth.api;

import com.oraskin.auth.domain.AuthUserProfile;
import com.oraskin.auth.domain.LoginResponse;
import com.oraskin.auth.domain.OAuthLoginRequest;
import com.oraskin.auth.service.AuthService;
import com.oraskin.common.auth.AuthenticatedUser;
import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.mvc.ControllerResult;
import com.oraskin.common.mvc.annotation.PathVariable;
import com.oraskin.common.mvc.annotation.PublicEndpoint;
import com.oraskin.common.mvc.annotation.RequestBody;
import com.oraskin.common.mvc.annotation.RequestMapping;
import com.oraskin.common.mvc.annotation.RestController;

import java.util.Map;

@RestController("/auth")
public final class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PublicEndpoint
    @RequestMapping(method = "POST", value = "/{provider}/login")
    public ControllerResult login(
            @PathVariable("provider") String provider,
            @RequestBody OAuthLoginRequest request
    ) {
        return authService.login(provider, request);
    }

    @PublicEndpoint
    @RequestMapping(method = "POST", value = "/refresh")
    public ControllerResult refresh(HttpRequest request) {
        return authService.refresh(request);
    }

    @RequestMapping(method = "GET", value = "/me")
    public AuthUserProfile me(AuthenticatedUser user) {
        return authService.getCurrentUser(user);
    }

    @RequestMapping(method = "POST", value = "/logout")
    public ControllerResult logout(AuthenticatedUser user, HttpRequest request) {
        return authService.logout(user, request);
    }
}
