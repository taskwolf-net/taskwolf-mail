package com.dulno.mail.action.delete;

import com.dulno.core.action.ActionExecutor;
import com.dulno.core.action.ActionResult;
import com.dulno.core.workflow.placeholder.PlaceholderDissolve;
import com.dulno.mail.structure.MailDatabaseTable;
import com.dulno.mail.structure.MailEntry;
import com.google.common.collect.Maps;
import lombok.AllArgsConstructor;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Session;
import javax.mail.search.HeaderTerm;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@AllArgsConstructor(staticName = "create")
public final class MailDeleteActionExecutor implements ActionExecutor {
  private final MailDatabaseTable mailDatabaseTable;
  private final UUID mailId;
  private String mailEntryId;

  @Override
  public CompletableFuture<ActionResult> execute(Map<String, Object> information) {
    var dissolve = PlaceholderDissolve.create(information);
    mailEntryId = dissolve.dissolve(mailEntryId);
    return mailDatabaseTable.mailExists(mailId).thenCompose(this::execute);
  }

  private CompletableFuture<ActionResult> execute(boolean mailExists) {
    if (!mailExists) {
      return ActionResult.futureFailure("mail.action.delete.failure.mail.not.found");
    }
    return mailDatabaseTable.findMail(mailId).thenApplyAsync(this::execute);
  }

  private ActionResult execute(MailEntry mail) {
    try {
      var session = createSession("imap", mail.imapHost(), mail.imapPort());
      var store = session.getStore("imap");
      store.connect(mail.imapHost(), mail.mailUser(), mail.mailPassword());
      var folder = store.getFolder("INBOX");
      folder.open(Folder.READ_WRITE);
      var messages = folder.search(new HeaderTerm("Message-ID", mailEntryId));
      if (messages.length > 0) {
        messages[0].setFlag(Flags.Flag.DELETED, true);
      }
      folder.close(true);
      store.close();
      if (messages.length > 0) {
        return ActionResult.success(buildInformation());
      }
      return ActionResult.failure("mail.action.delete.failure.mail.entry.not.found");
    } catch (Exception exception) {
      return ActionResult.failure(exception.getMessage());
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

  private Map<String, Object> buildInformation() {
    var information = Maps.<String, Object>newHashMap();
    information.put("mailEntryIdentifier", mailEntryId);
    return information;
  }
}
