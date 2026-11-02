package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.authz.service.AuthzManifestService;
import com.flowbinary.platformservice.authz.service.AuthzServiceIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/** Services declare the privileges they protect, with their own service token. */
@RestController
@RequestMapping("/api/v1/internal/authz/manifests")
@RequiredArgsConstructor
@Tag(name = "Authorization manifests", description = "Called by services with their service token")
public class AuthzManifestController {

    static final int MAX_BYTES = 256 * 1024;

    private final AuthzServiceIdentity identity;
    private final AuthzManifestService manifests;

    @PutMapping(value = "/{service}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Declare this service's privileges",
            description = "Creates and updates the service's privileges, deprecates the ones no longer declared and opens "
                    + "template-grant proposals for a platform operator. An invalid manifest changes nothing and answers 422 "
                    + "with every problem. Identical content is a no-op.")
    public ResponseEntity<AuthzManifestService.Result> put(Authentication auth, @PathVariable String service,
                                                           @RequestBody String body) {
        String clientId = identity.requireOwner(auth, service);
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        AuthzManifestService.Result result = manifests.accept(service, body, clientId);
        return ResponseEntity.status(result.outcome() == AuthzManifestService.Outcome.REJECTED
                ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.OK).body(result);
    }
}
