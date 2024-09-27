package com.dulno.mail.action.send;

import com.dulno.core.action.Action;
import com.dulno.core.action.ActionContentDatabaseTable;
import com.dulno.core.action.ActionInformation;
import com.dulno.core.database.*;
import com.dulno.core.workflow.component.input.InputComponentDataType;
import com.dulno.core.workflow.component.input.InputComponentSelect;
import com.dulno.core.workflow.component.input.InputComponentVariable;
import com.dulno.core.workflow.component.output.OutputComponentVariable;
import com.dulno.mail.structure.MailDatabaseTable;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@AllArgsConstructor(staticName = "create")
public final class MailSendAction implements Action<MailSendActionExecutor> {
  public static MailSendAction create(
    InputComponentSelect tableComponentSelect,
    MailDatabaseTable mailDatabaseTable,
    DatabaseConnection databaseConnection, DatabaseKeyspace databaseKeyspace
  ) {
    var contentColumns = Lists.<DatabaseColumn>newArrayList();
    contentColumns.add(DatabaseColumn.create("mailId", DatabaseDataType.UUID));
    contentColumns.add(DatabaseColumn.create("mailPrefix", DatabaseDataType.TEXT));
    contentColumns.add(DatabaseColumn.create("mailReceiver", DatabaseDataType.TEXT));
    contentColumns.add(DatabaseColumn.create("mailTitle", DatabaseDataType.TEXT));
    contentColumns.add(DatabaseColumn.create("mailBody", DatabaseDataType.TEXT));
    return new MailSendAction(tableComponentSelect, mailDatabaseTable,
      ActionContentDatabaseTable.create(databaseConnection, databaseKeyspace,
        "action_mail_send", contentColumns));
  }

  private final InputComponentSelect tableComponentSelect;
  private final MailDatabaseTable mailDatabaseTable;
  private final ActionContentDatabaseTable contentDatabaseTable;

  @Override
  public String type() {
    return "mail-send-action";
  }

  @Override
  public ActionInformation information() {
    return ActionInformation.builder()
      .withName("mail.action.send.name")
      .withDescription("mail.action.send.description")
      .withInputVariable(InputComponentVariable.createSelect("mail.action.send.input.mail.name",
        "mailIdentifier", "mail.action.send.input.mail.description", tableComponentSelect))
      .withInputVariable(InputComponentVariable.createRequired("mail.action.send.input.prefix.name",
        "mailPrefix", "mail.action.send.input.prefix.description",
        "mail.action.send.input.prefix.placeholder", InputComponentDataType.TEXT))
      .withInputVariable(InputComponentVariable.createRequired("mail.action.send.input.receiver.name",
        "mailReceiver", "mail.action.send.input.receiver.description",
        "mail.action.send.input.receiver.placeholder", InputComponentDataType.TEXT))
      .withInputVariable(InputComponentVariable.createRequired("mail.action.send.input.title.name",
        "mailTitle", "mail.action.send.input.title.description", InputComponentDataType.TEXT))
      .withInputVariable(InputComponentVariable.createRequired("mail.action.send.input.body.name",
        "mailBody", "mail.action.send.input.body.description", InputComponentDataType.TEXT))
      .withOutputVariable(OutputComponentVariable.create("mail.action.send.output.identifier", "mailEntryIdentifier"))
      .withOutputVariable(OutputComponentVariable.create("mail.action.send.output.sender", "mailSender"))
      .withOutputVariable(OutputComponentVariable.create("mail.action.send.output.receiver", "mailReceiver"))
      .withOutputVariable(OutputComponentVariable.create("mail.action.send.output.title", "mailTitle"))
      .withOutputVariable(OutputComponentVariable.create("mail.action.send.output.body", "mailBody"))
      .build();
  }

  @Override
  public void initialize() {
    contentDatabaseTable.createIfNotExists();
  }

  @Override
  public CompletableFuture<Void> insert(UUID actionId, Map<String, Object> content) {
    return contentDatabaseTable.insertContent(actionId, DatabaseRow.of(
      content.get("mailIdentifier"), content.get("mailPrefix"),
      content.get("mailReceiver"), content.get("mailTitle"),
      content.get("mailBody")));
  }

  @Override
  public CompletableFuture<Map<String, Object>> findContent(UUID triggerId) {
    return contentDatabaseTable.findContent(triggerId).thenApply(row ->
      Map.of("mailIdentifier", row.findCell(1).uuidValue(),
        "mailPrefix", row.findCell(2).stringValue(),
        "mailReceiver", row.findCell(3).stringValue(),
        "mailTitle", row.findCell(4).stringValue(),
        "mailBody", row.findCell(5).stringValue()));
  }

  @Override
  public CompletableFuture<MailSendActionExecutor> build(UUID actionId) {
    return contentDatabaseTable.findContent(actionId)
      .thenApply(content -> MailSendActionExecutor.create(mailDatabaseTable,
        content.findCell(1).uuidValue(), content.findCell(2).stringValue(),
        content.findCell(3).stringValue(), content.findCell(4).stringValue(),
        content.findCell(5).stringValue()));
  }

  @Override
  public CompletableFuture<Void> delete(UUID actionId) {
    return contentDatabaseTable.deleteContent(actionId);
  }
}
