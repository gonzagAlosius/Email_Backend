package Email_backend.Email_backend.controller;

import Email_backend.Email_backend.service.EncryptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/debug")
@CrossOrigin(origins = "*")
public class DebugController {

  @Autowired
  private EncryptionService encryptionService;

  @GetMapping("/decrypt")
  public String decrypt(@RequestParam("value") String value) {
    try {
      return encryptionService.decrypt(value);
    } catch (Exception e) {
      return "Error: " + e.getMessage();
    }
  }

  @GetMapping("/encrypt")
  public String encrypt(@RequestParam("value") String value) {
    try {
      return encryptionService.encrypt(value);
    } catch (Exception e) {
      return "Error: " + e.getMessage();
    }
  }
}
