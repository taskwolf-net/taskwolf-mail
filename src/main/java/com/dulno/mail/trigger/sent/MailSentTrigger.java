package com.dulno.mail.trigger.sent;

import com.dulno.core.database.*;
import com.dulno.core.database.condition.DatabaseCondition;
import com.dulno.workflow.trigger.Trigger;
import com.dulno.workflow.trigger.TriggerContentDatabaseTable;
import com.dulno.workflow.trigger.TriggerInformation;
import com.dulno.workflow.component.input.InputComponentSelect;
import com.dulno.workflow.component.input.InputComponentVariable;
import com.dulno.workflow.component.output.OutputComponentVariable;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor(staticName = "create")
public final class MailSentTrigger implements Trigger {
  public static MailSentTrigger create(
    InputComponentSelect mailComponentSelect,
    DatabaseConnection databaseConnection, DatabaseKeyspace databaseKeyspace
  ) {
    var contentColumns = Lists.<DatabaseColumn>newArrayList();
    contentColumns.add(DatabaseColumn.create("mailId", DatabaseDataType.UUID));
    return new MailSentTrigger(mailComponentSelect,
      TriggerContentDatabaseTable.create(databaseConnection, databaseKeyspace,
        "trigger_mail_sent", contentColumns));
  }

  private final InputComponentSelect mailComponentSelect;
  private final TriggerContentDatabaseTable contentDatabaseTable;

  @Override
  public String type() {
    return "mail-sent-trigger";
  }

  @Override
  public TriggerInformation information() {
    return TriggerInformation.builder()
      .withName("mail.trigger.sent.name")
      .withDescription("mail.trigger.sent.description")
      .withInputVariable(InputComponentVariable.createSelect("mail.trigger.sent.input.mail.name",
        "mailIdentifier", "mail.trigger.sent.input.mail.description", mailComponentSelect))
      .withOutputVariable(OutputComponentVariable.create("mail.trigger.sent.output.identifier", "mailEntryIdentifier"))
      .withOutputVariable(OutputComponentVariable.create("mail.trigger.sent.output.sender", "mailSender"))
      .withOutputVariable(OutputComponentVariable.create("mail.trigger.sent.output.prefix", "mailPrefix"))
      .withOutputVariable(OutputComponentVariable.create("mail.trigger.sent.output.title", "mailTitle"))
      .withOutputVariable(OutputComponentVariable.create("mail.trigger.sent.output.body", "mailBody"))
      .build();
  }

  @Override
  public void initialize() {
    contentDatabaseTable.createIfNotExists();
  }

  @Override
  public CompletableFuture<Void> insert(UUID triggerId, Map<String, Object> content) {
    return contentDatabaseTable.insertContent(triggerId,
      DatabaseRow.of(UUID.fromString((String) content.get("mailIdentifier"))));
  }

  @Override
  public CompletableFuture<Map<String, Object>> findContent(UUID triggerId) {
    return contentDatabaseTable.findContent(triggerId).thenApply(row ->
      Map.of("mailIdentifier", row.findCell(1).uuidValue().toString()));
  }

  @Override
  public CompletableFuture<List<UUID>> findEntries(DatabaseCondition condition) {
    return contentDatabaseTable.findContentByCondition(condition).thenApply(
      rows -> rows.stream().map(row -> row.findCell(0).uuidValue()).toList());
  }

  @Override
  public CompletableFuture<Void> delete(UUID triggerId) {
    return contentDatabaseTable.deleteContent(triggerId);
  }
}
