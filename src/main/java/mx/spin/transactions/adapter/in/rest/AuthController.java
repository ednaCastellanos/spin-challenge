package mx.spin.transactions.adapter.in.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import mx.spin.transactions.config.SecurityProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Emisión de tokens (usuarios en memoria: demo/demo)")
public class AuthController {

    public record TokenRequest(@NotBlank String username, @NotBlank String password) { }
    public record TokenResponse(String accessToken, String tokenType, long expiresIn) { }

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;

    public AuthController(AuthenticationManager authenticationManager, JwtEncoder jwtEncoder,
                          SecurityProperties properties) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    @PostMapping("/token")
    @Operation(summary = "Emite un JWT para credenciales válidas")
    public TokenResponse token(@RequestBody TokenRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("spin-transactions")
                .issuedAt(now)
                .expiresAt(now.plus(properties.expiration()))
                .subject(auth.getName())
                .claim("scope", "transactions:write transactions:read")
                .build();

        // Sin JwsHeader explícito, NimbusJwtEncoder asume RS256 y no encuentra clave RSA.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.expiration().toSeconds());
    }
}