package com.dulno.mail.access;


import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.organization.team.TeamTargetDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserTargetDatabaseTable;
import com.dulno.mail.structure.MailDatabaseTable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public class MailController extends DulnoRestController {
  private final MailDatabaseTable mailDatabaseTable;
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final TeamTargetDatabaseTable teamTargetDatabaseTable;

  private MailController(
    Key productKey, UserDatabaseTable userDatabaseTable,
    MailDatabaseTable mailDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable
  ) {
    super(productKey, userDatabaseTable);
    this.mailDatabaseTable = mailDatabaseTable;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.teamTargetDatabaseTable = teamTargetDatabaseTable;
  }

  @RequestMapping(path = "/mail/add/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> addMail(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var domain = body.getString("domain");
    return findUser(request)
      .thenCompose(user -> userTargetDatabaseTable.findTargetSecured(user.id())
        .thenCompose(target -> findMailOwner(user, target)
          .thenCompose(owner -> mailDatabaseTable.mailExists(owner, domain)
            .thenCompose(mailExists -> addMail(owner, domain,
              body.getInt("smtpPort"), body.getInt("imapPort"), mailExists)))));
  }

  private CompletableFuture<UUID> findMailOwner(User user, UUID target) {
    return user.id().equals(target) ?
      CompletableFuture.completedFuture(target) :
      teamTargetDatabaseTable.findTargetSecured(user.id())
        .thenApply(team -> team.orElse(target));
  }

  private CompletableFuture<Map<String, Object>> addMail(
    UUID ownerId, String domain, int smtpPort, int imapPort, boolean mailExists
  ) {
    if (mailExists) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return mailDatabaseTable.generateAvailableMailId().thenCompose(id ->
      mailDatabaseTable.insertMail(id, ownerId, domain, smtpPort, imapPort)
        .thenApply(value -> Map.of("success", true)));
  }
}
