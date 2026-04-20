package com.oraskin.resource;

import com.oraskin.chat.key.service.PublicKeyService;
import com.oraskin.chat.key.value.PrivateChatKeyView;
import com.oraskin.chat.key.value.RegisterPrivateChatKeyRequest;
import com.oraskin.common.auth.AuthenticatedUser;
import com.oraskin.common.mvc.annotation.RequestBody;
import com.oraskin.common.mvc.annotation.RequestMapping;
import com.oraskin.common.mvc.annotation.RestController;

@RestController("/public-keys")
public final class PublicKeysController {

    private final PublicKeyService publicKeyService;

    public PublicKeysController(PublicKeyService publicKeyService) {
        this.publicKeyService = publicKeyService;
    }

    @RequestMapping(method = "POST")
    public PrivateChatKeyView registerCurrentKeyPost(
            AuthenticatedUser user,
            @RequestBody RegisterPrivateChatKeyRequest request
    ) {
        return publicKeyService.registerCurrentKey(user.userId(), request);
    }
}
