package net.taskwolf.mail;

import net.taskwolf.mail.structure.MailDatabaseTable;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(staticName = "create")
public final class MailContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final MailDatabaseTable mailDatabaseTable;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("mailDatabaseTable", mailDatabaseTable);
  }
}
