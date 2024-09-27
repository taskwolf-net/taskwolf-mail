package com.dulno.mail.action.send;

import com.dulno.core.action.ActionExecutor;
import com.dulno.core.action.ActionResult;
import com.dulno.core.workflow.placeholder.PlaceholderDissolve;
import com.dulno.mail.structure.MailDatabaseTable;
import com.dulno.mail.structure.MailEntry;
import com.google.common.collect.Maps;
import lombok.AllArgsConstructor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@AllArgsConstructor(staticName = "create")
public final class MailSendActionExecutor implements ActionExecutor {
  private final MailDatabaseTable mailDatabaseTable;
  private final UUID mailId;
  private String mailPrefix;
  private String mailReceiver;
  private String mailTitle;
  private String mailBody;

  @Override
  public CompletableFuture<ActionResult> execute(Map<String, Object> information) {
    var dissolve = PlaceholderDissolve.create(information);
    mailPrefix = dissolve.dissolve(mailPrefix);
    mailReceiver = dissolve.dissolve(mailReceiver);
    mailTitle = dissolve.dissolve(mailTitle);
    mailBody = dissolve.dissolve(mailBody);
    return mailDatabaseTable.mailExists(mailId)
      .thenCompose(exists -> execute(information, exists));
  }

  private CompletableFuture<ActionResult> execute(
    Map<String, Object> information, boolean mailExists
  ) {
    if (!mailExists) {
      return ActionResult.futureFailure("mail.action.send.failure.mail.not.found");
    }
    return mailDatabaseTable.findMail(mailId)
      .thenApply(mail -> execute(information, mail));
  }

  private ActionResult execute(
    Map<String, Object> information, MailEntry mail
  ) {
    //TODO: SEND MAIL
    return ActionResult.success(buildInformation(mail, ""));
  }

  private Map<String, Object> buildInformation(MailEntry mail, String entryIdentifier) {
    var information = Maps.<String, Object>newHashMap();
    information.put("mailEntryIdentifier", entryIdentifier);
    information.put("mailSender", mailPrefix + "@" + mail.domain());
    information.put("mailReceiver", mailReceiver);
    information.put("mailTitle", mailTitle);
    information.put("mailBody", mailBody);
    return information;
  }
}
