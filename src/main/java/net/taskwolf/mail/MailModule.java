package net.taskwolf.mail;

import net.taskwolf.core.database.DatabaseConnection;
import net.taskwolf.core.database.DatabaseKeyspace;
import net.taskwolf.mail.action.delete.MailDeleteAction;
import net.taskwolf.mail.action.send.MailSendAction;
import net.taskwolf.mail.select.MailComponentSelect;
import net.taskwolf.mail.structure.MailDatabaseTable;
import net.taskwolf.mail.trigger.MailCheckSchedule;
import net.taskwolf.mail.trigger.receive.MailReceiveTrigger;
import net.taskwolf.mail.trigger.sent.MailSentTrigger;
import net.taskwolf.workflow.WorkflowModule;
import net.taskwolf.workflow.integration.Integration;
import com.google.common.collect.Lists;
import com.google.inject.Injector;
import net.taskwolf.core.account.AccountLink;
import net.taskwolf.workflow.action.ActionRepository;
import net.taskwolf.core.log.Log;
import net.taskwolf.core.module.ModuleDescription;
import net.taskwolf.core.module.ModuleInformation;
import net.taskwolf.core.module.ModuleLoadPriority;
import net.taskwolf.workflow.trigger.TriggerRepository;
import net.taskwolf.workflow.component.input.InputComponentSelect;
import org.springframework.boot.SpringApplication;

@ModuleDescription(name = "mail", version = "1.0.0-SNAPSHOT",
  priority = ModuleLoadPriority.NEUTRAL)
public final class MailModule extends Integration {
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
      injector().getInstance(WorkflowModule.class), mailDatabaseTable);
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
    var mailDatabaseTable = injector().getInstance(MailDatabaseTable.class);
    var repository = TriggerRepository.create();
    repository.registerTrigger(MailReceiveTrigger.create(mailDatabaseTable,
      mailComponentSelect, databaseConnection, databaseKeyspace));
    repository.registerTrigger(MailSentTrigger.create(mailDatabaseTable,
      mailComponentSelect, databaseConnection, databaseKeyspace));
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