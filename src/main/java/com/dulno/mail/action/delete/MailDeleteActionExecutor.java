package com.dulno.mail.action.delete;

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
public final class MailDeleteActionExecutor implements ActionExecutor {
  private final MailDatabaseTable mailDatabaseTable;
  private final UUID mailId;
  private String mailEntryId;

  @Override
  public CompletableFuture<ActionResult> execute(Map<String, Object> information) {
    var dissolve = PlaceholderDissolve.create(information);
    mailEntryId = dissolve.dissolve(mailEntryId);
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
    //TODO: DELETE MAIL
    return ActionResult.success(buildInformation());
  }

  private Map<String, Object> buildInformation() {
    var information = Maps.<String, Object>newHashMap();
    information.put("mailEntryIdentifier", mailEntryId);
    return information;
  }
}
