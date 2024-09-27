package com.dulno.mail;

import com.dulno.core.database.DatabaseConnection;
import com.dulno.core.database.DatabaseKeyspace;
import com.dulno.mail.structure.MailDatabaseTable;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(staticName = "create")
public class MailInjectionModule extends AbstractModule {
  @Override
  protected void configure() {

  }

  @Provides
  @Singleton
  MailDatabaseTable provideMailDatabaseTable(
    DatabaseConnection connection, DatabaseKeyspace keyspace
  ) {
    var mailDatabaseTable = MailDatabaseTable.create(connection, keyspace);
    mailDatabaseTable.createIfNotExists();
    return mailDatabaseTable;
  }
}
