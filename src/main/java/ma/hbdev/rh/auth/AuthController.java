package ma.hbdev.rh.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal Auth controller – real endpoints will be added later.
 */
@RestController
public class AuthController {

    @GetMapping("/api/health")
    public String healthCheck() {
        return "OK";
    }
}
