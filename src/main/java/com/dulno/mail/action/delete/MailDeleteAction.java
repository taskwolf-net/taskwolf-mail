package com.dulno.mail.action.delete;

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
public final class MailDeleteAction implements Action<MailDeleteActionExecutor> {
  public static MailDeleteAction create(
    InputComponentSelect mailComponentSelect,
    MailDatabaseTable mailDatabaseTable,
    DatabaseConnection databaseConnection, DatabaseKeyspace databaseKeyspace
  ) {
    var contentColumns = Lists.<DatabaseColumn>newArrayList();
    contentColumns.add(DatabaseColumn.create("mailId", DatabaseDataType.UUID));
    contentColumns.add(DatabaseColumn.create("mailEntryId", DatabaseDataType.TEXT));
    return new MailDeleteAction(mailComponentSelect, mailDatabaseTable,
      ActionContentDatabaseTable.create(databaseConnection, databaseKeyspace,
        "action_mail_delete", contentColumns));
  }

  private final InputComponentSelect mailComponentSelect;
  private final MailDatabaseTable mailDatabaseTable;
  private final ActionContentDatabaseTable contentDatabaseTable;

  @Override
  public String type() {
    return "mail-delete-action";
  }

  @Override
  public ActionInformation information() {
    return ActionInformation.builder()
      .withName("mail.action.delete.name")
      .withDescription("mail.action.delete.description")
      .withInputVariable(InputComponentVariable.createSelect("mail.action.delete.input.mail.name",
        "mailIdentifier", "mail.action.delete.input.mail.description", mailComponentSelect))
      .withInputVariable(InputComponentVariable.createRequired("mail.action.delete.input.entry.name",
        "mailEntryIdentifier", "mail.action.delete.input.entry.description", InputComponentDataType.TEXT))
      .withOutputVariable(OutputComponentVariable.create("mail.action.delete.output.identifier", "mailEntryIdentifier"))
      .build();
  }

  @Override
  public void initialize() {
    contentDatabaseTable.createIfNotExists();
  }

  @Override
  public CompletableFuture<Void> insert(UUID actionId, Map<String, Object> content) {
    return contentDatabaseTable.insertContent(actionId, DatabaseRow.of(
      content.get("mailIdentifier"), content.get("mailEntryIdentifier")));
  }

  @Override
  public CompletableFuture<Map<String, Object>> findContent(UUID triggerId) {
    return contentDatabaseTable.findContent(triggerId).thenApply(row ->
      Map.of("mailIdentifier", row.findCell(1).uuidValue(),
        "mailEntryIdentifier", row.findCell(2).stringValue()));
  }

  @Override
  public CompletableFuture<MailDeleteActionExecutor> build(UUID actionId) {
    return contentDatabaseTable.findContent(actionId)
      .thenApply(content -> MailDeleteActionExecutor.create(mailDatabaseTable,
        content.findCell(1).uuidValue(), content.findCell(2).stringValue()));
  }

  @Override
  public CompletableFuture<Void> delete(UUID actionId) {
    return contentDatabaseTable.deleteContent(actionId);
  }
}