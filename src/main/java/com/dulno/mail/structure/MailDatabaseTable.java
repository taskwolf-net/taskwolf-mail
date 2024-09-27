package com.dulno.mail.structure;

import com.dulno.core.database.*;
import com.dulno.core.database.condition.DatabaseComparison;
import com.dulno.core.database.condition.DatabaseCondition;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class MailDatabaseTable extends DatabaseTable {
  private static final String TABLE_NAME = "mail";

  public static MailDatabaseTable create(
    DatabaseConnection connection, DatabaseKeyspace keyspace
  ) {
    var columns = Lists.<DatabaseColumn>newArrayList();
    columns.add(DatabaseColumn.create("id", DatabaseDataType.UUID,
      DatabaseColumn.Type.PRIMARY_KEY));
    columns.add(DatabaseColumn.create("owner", DatabaseDataType.UUID));
    columns.add(DatabaseColumn.create("domain", DatabaseDataType.TEXT));
    columns.add(DatabaseColumn.create("smtpPort", DatabaseDataType.INT));
    columns.add(DatabaseColumn.create("imapPort", DatabaseDataType.INT));
    return new MailDatabaseTable(connection, keyspace, TABLE_NAME, columns);
  }

  private MailDatabaseTable(
    DatabaseConnection connection, DatabaseKeyspace keyspace, String name,
    List<DatabaseColumn> columns
  ) {
    super(connection, keyspace, name, columns);
  }

  public CompletableFuture<Void> insertMail(MailEntry mail) {
    return insertMail(mail.id(), mail.ownerId(), mail.domain(), mail.smtpPort(),
      mail.imapPort());
  }

  public CompletableFuture<Void> insertMail(
    UUID id, UUID ownerId, String domain, int smtpPort, int imapPort
  ) {
    return insert(DatabaseRow.of(id, ownerId, domain, smtpPort, imapPort));
  }

  public CompletableFuture<UUID> generateAvailableMailId() {
    var futureResponse = new CompletableFuture<UUID>();
    var id = UUID.randomUUID();
    mailExists(id).thenApply(exists -> exists ?
      generateAvailableMailId().thenApply(futureResponse::complete) :
      CompletableFuture.completedFuture(futureResponse.complete(id)));
    return futureResponse;
  }

  public void deleteMail(UUID id) {
    delete(id);
  }

  public CompletableFuture<Boolean> mailExists(UUID id) {
    return exists(id);
  }

  public CompletableFuture<Boolean> mailExists(UUID ownerId, String domain) {
    return exists(DatabaseCondition.of(DatabaseComparison.create("owner", ownerId),
      DatabaseComparison.create("domain", domain)));
  }

  public CompletableFuture<MailEntry> findMail(UUID id) {
    return selectRow(id).thenApply(MailEntry::of);
  }

  public CompletableFuture<List<MailEntry>> findMailsOfOwner(UUID ownerId) {
    var condition = DatabaseCondition.of(
      DatabaseComparison.create("owner", ownerId));
    return selectRows(condition).thenApply(rows ->
      rows.stream().map(MailEntry::of).toList());
  }
}
