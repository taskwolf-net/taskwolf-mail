package net.taskwolf.mail;

import net.taskwolf.core.database.DatabaseConnection;
import net.taskwolf.core.database.DatabaseKeyspace;
import net.taskwolf.mail.structure.MailDatabaseTable;
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
    return MailDatabaseTable.create(connection, keyspace);
  }
}
