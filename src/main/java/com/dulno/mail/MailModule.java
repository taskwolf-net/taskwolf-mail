package com.dulno.mail;

import com.dulno.core.CoreModule;
import com.dulno.core.database.DatabaseConnection;
import com.dulno.core.database.DatabaseKeyspace;
import com.dulno.mail.action.delete.MailDeleteAction;
import com.dulno.mail.action.send.MailSendAction;
import com.dulno.mail.select.MailComponentSelect;
import com.dulno.mail.structure.MailDatabaseTable;
import com.dulno.mail.trigger.MailCheckSchedule;
import com.dulno.mail.trigger.receive.MailReceiveTrigger;
import com.dulno.mail.trigger.sent.MailSentTrigger;
import com.google.common.collect.Lists;
import com.google.inject.Injector;
import com.dulno.core.account.AccountLink;
import com.dulno.core.action.ActionRepository;
import com.dulno.core.log.Log;
import com.dulno.core.module.Module;
import com.dulno.core.module.ModuleDescription;
import com.dulno.core.module.ModuleInformation;
import com.dulno.core.module.ModuleLoadPriority;
import com.dulno.core.trigger.TriggerRepository;
import com.dulno.core.workflow.component.input.InputComponentSelect;
import org.springframework.boot.SpringApplication;

@ModuleDescription(name = "mail", version = "1.0.0-SNAPSHOT",
  priority = ModuleLoadPriority.NEUTRAL)
public final class MailModule extends Module {
  private Log log;
  private SpringApplication springApplication;
  private MailContextInitializer contextInitializer;
  private AccountLink accountLink;
  private InputComponentSelect mailComponentSelect;
  private MailCheckSchedule mailCheckSchedule;

  public MailModule(Injector injector) {
    super(injector.createChildInjector(MailInjectionModule.create()));
  }

  @Override
  public void enable() throws Exception {
    log = injector().getInstance(Log.class).subLog("Mail");
    springApplication = injector().getInstance(SpringApplication.class);
    var mailDatabaseTable = injector().getInstance(MailDatabaseTable.class);
    contextInitializer = MailContextInitializer.create(mailDatabaseTable);
    springApplication.addInitializers(contextInitializer);
    accountLink = MailAccountLink.create(mailDatabaseTable);
    mailComponentSelect = MailComponentSelect.create(mailDatabaseTable);
    startMailCheckSchedule(mailDatabaseTable);
  }

  private void startMailCheckSchedule(MailDatabaseTable mailDatabaseTable) {
    mailCheckSchedule = MailCheckSchedule.create(
      injector().getInstance(CoreModule.class), mailDatabaseTable);
    mailCheckSchedule.start();
  }

  @Override
  public void disable() {
    mailCheckSchedule.stop();
    var initializers = Lists.newArrayList(springApplication.getInitializers());
    initializers.remove(contextInitializer);
    springApplication.setInitializers(initializers);
  }

  @Override
  public AccountLink accountLink() {
    return accountLink;
  }

  @Override
  public ModuleInformation moduleInformation() {
    return ModuleInformation.create("Mail", "", "mail",
      ModuleInformation.Type.PUBLIC);
  }

  @Override
  public TriggerRepository triggerRepository() {
    var databaseConnection = injector().getInstance(DatabaseConnection.class);
    var databaseKeyspace = injector().getInstance(DatabaseKeyspace.class);
    var repository = TriggerRepository.create();
    repository.registerTrigger(MailReceiveTrigger.create(mailComponentSelect,
      databaseConnection, databaseKeyspace));
    repository.registerTrigger(MailSentTrigger.create(mailComponentSelect,
      databaseConnection, databaseKeyspace));
    return repository;
  }


  @Override
  public ActionRepository actionRepository() {
    var databaseConnection = injector().getInstance(DatabaseConnection.class);
    var databaseKeyspace = injector().getInstance(DatabaseKeyspace.class);
    var mailDatabaseTable = injector().getInstance(MailDatabaseTable.class);
    var repository = ActionRepository.create();
    repository.registerAction(MailSendAction.create(mailComponentSelect,
      mailDatabaseTable, databaseConnection, databaseKeyspace));
    repository.registerAction(MailDeleteAction.create(mailComponentSelect,
      mailDatabaseTable, databaseConnection, databaseKeyspace));
    return repository;
  }
}