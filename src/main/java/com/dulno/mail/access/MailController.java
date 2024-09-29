package com.dulno.mail.access;


import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.organization.team.TeamTargetDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserTargetDatabaseTable;
import com.dulno.mail.structure.MailDatabaseTable;
import com.dulno.mail.structure.MailEntry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Session;
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
              body.getString("mailUser"), body.getString("mailPassword"),
              body.getString("smtpHost"), body.getInt("smtpPort"),
              body.getString("imapHost"), body.getInt("imapPort"), mailExists)))));
  }

  private CompletableFuture<UUID> findMailOwner(User user, UUID target) {
    return user.id().equals(target) ?
      CompletableFuture.completedFuture(target) :
      teamTargetDatabaseTable.findTargetSecured(user.id())
        .thenApply(team -> team.orElse(target));
  }

  private CompletableFuture<Map<String, Object>> addMail(
    UUID ownerId, String domain, String mailUser, String mailPassword,
    String smtpHost, int smtpPort, String imapHost, int imapPort,
    boolean mailExists
  ) {
    if (mailExists) {
      return CompletableFuture.completedFuture(Map.of("success", false));
    }
    return mailDatabaseTable.generateAvailableMailId()
      .thenApply(id -> MailEntry.create(id, ownerId, domain, mailUser,
        mailPassword, smtpHost, smtpPort, imapHost, imapPort))
      .thenCompose(mail -> mailDatabaseTable.insertMail(mail)
        .thenAcceptAsync(value -> setupMail(mail))
        .thenApply(value -> Map.of("success", true)));
  }

  private static final Flags RECEIVE_FLAG = new Flags("DULNO-RECEIVE");
  private static final Flags SENT_FLAG = new Flags("DULNO-SENT");

  private void setupMail(MailEntry mail) {
    try {
      var session = createSession("imap", mail.imapHost(), mail.imapPort());
      var store = session.getStore("imap");
      store.connect(mail.imapHost(), mail.mailUser(), mail.mailPassword());
      var inboxFolder = store.getFolder("INBOX");
      inboxFolder.open(Folder.READ_WRITE);
      var inboxEntries = inboxFolder.getMessages();
      for (var entry : inboxEntries) {
        entry.setFlags(RECEIVE_FLAG, true);
      }
      var sentFolder = store.getFolder("Sent");
      sentFolder.open(Folder.READ_WRITE);
      var sentEntries = sentFolder.getMessages();
      for (var entry : sentEntries) {
        entry.setFlags(SENT_FLAG, true);
      }
      inboxFolder.close(true);
      sentFolder.close(true);
      store.close();
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  private Session createSession(String protocol, String host, int port) {
    var properties = System.getProperties();
    properties.put("mail." + protocol + ".host", host);
    properties.put("mail." + protocol + ".port", port);
    properties.put("mail." + protocol + ".starttls.enable", "true");
    properties.put("mail." + protocol + ".socketFactory.class",
      "javax.net.ssl.SSLSocketFactory");
    var session = Session.getDefaultInstance(properties, null);
    session.setDebug(false);
    return session;
  }
}
