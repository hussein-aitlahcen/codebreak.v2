package com.codebreak.login.network.handler.impl;

import java.util.Optional;
import java.util.function.BiFunction;
import com.codebreak.common.network.message.AbstractDofusMessage;
import com.codebreak.common.persistence.Database;
import com.codebreak.login.database.account.impl.AccountByName;
import com.codebreak.login.database.account.impl.Create;
import com.codebreak.login.database.account.impl.Existant;
import com.codebreak.login.database.account.impl.NonExistant;
import com.codebreak.login.database.account.impl.NotBanned;
import com.codebreak.login.database.account.impl.NotConnected;
import com.codebreak.login.database.account.impl.PasswordMatch;
import com.codebreak.login.database.account.impl.exception.AlreadyConnectedException;
import com.codebreak.login.database.account.impl.exception.BannedException;
import com.codebreak.login.database.account.impl.exception.ExistantException;
import com.codebreak.login.database.account.impl.exception.NonExistantException;
import com.codebreak.login.database.account.impl.exception.WrongPasswordException;
import com.codebreak.login.network.LoginClient;
import com.codebreak.login.network.handler.AbstractLoginHandler;
import com.codebreak.login.network.handler.AbstractLoginState;
import com.codebreak.login.network.handler.LoginState;
import com.codebreak.login.network.message.LoginMessage;
import com.codebreak.login.network.structure.GameServiceSource;
import com.codebreak.login.persistence.tables.records.AccountRecord;

public final class AuthenticationHandler extends AbstractLoginHandler {
	
	private final GameServiceSource gameServiceSource;
	
	public AuthenticationHandler(final AbstractLoginState<?> parent, final Database db, final GameServiceSource gameServiceSource) {
		super(parent, db, AUTHENTICATION);
		this.gameServiceSource = gameServiceSource;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected Optional<BiFunction<LoginClient, String, Optional<LoginState>>> getHandler(
			String message) {
		return Optional.of(this::authenticationRequest);
	}	
	
	private final void createNew(final String name) throws Exception {
		new Create(
				new NonExistant(
						new AccountByName(db(), name)
					),
					db(),
					name, 
					name, 
					"test", 
					"test@test.test", 
					"test", 
					"test"
				)
				.fetch();
	}
	
	private final Optional<AccountRecord> ensureAccountState(final String name, final String encryptKey, final String password) throws Exception {
		return new NotConnected(
				new NotBanned(
						new PasswordMatch(
								new Existant(
										new AccountByName(db(), name)
								), 
								encryptKey,
								password
						)
				)
			).fetch();
	}
	
	private final Optional<AccountRecord> checkAccount(final String encryptKey, final String message) throws Exception {
		final String[] accountData = message.split("#1");
		LOGGER.debug(message);
		final String name = accountData[0];
		final String password = accountData[1];		
		final String HEADER_NEW = "new";
		if(name.startsWith(HEADER_NEW)) {
			createNew(name.substring(HEADER_NEW.length()));
		}
		return ensureAccountState(name, encryptKey, password);
	}
	
	private final Optional<LoginState> authenticationRequest(final LoginClient client, final String message) {
		try {
			final AccountRecord account = checkAccount(client.encryptKey(), message).get();
			client.write(
				AbstractDofusMessage.batch(
					LoginMessage.LOGIN_SUCCESS(account.getPower()),
					LoginMessage.ACCOUNT_NICKNAME(account.getNickname()),
					LoginMessage.ACCOUNT_SECRET_QUESTION(account.getSecretquestion()),
					LoginMessage.ACCOUNT_HOSTS(this.gameServiceSource.gameServices())
				)
			);			
			return next(new ServerSelectionState(db(), account, this.gameServiceSource));
		} 
		catch (final NonExistantException | ExistantException | WrongPasswordException e) {
			client.write(LoginMessage.LOGIN_FAILURE_CREDENTIALS);
		}				
		catch (final BannedException e) {
			client.write(LoginMessage.LOGIN_FAILURE_BANNED);
		}
		catch(final AlreadyConnectedException e) {
			client.write(LoginMessage.LOGIN_FAILURE_CONNECTED);				
		} 
		catch (final Exception e) {
			client.closeChannel();
			e.printStackTrace();
		}
		return fail();
	}
}
