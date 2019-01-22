/*
 * Vogon personal finance/expense analyzer.
 * Licensed under Apache 2.0 License: http://www.apache.org/licenses/LICENSE-2.0
 * Author: Dmitry Zolotukhin <zlogic@gmail.com>
 */
package org.zlogic.vogon.web;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Currency;
import java.util.Date;
import java.util.List;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.zlogic.vogon.data.FinanceAccount;
import org.zlogic.vogon.data.FinanceTransaction;
import org.zlogic.vogon.data.TransactionComponent;
import org.zlogic.vogon.data.VogonUser;
import org.zlogic.vogon.web.data.AccountRepository;
import org.zlogic.vogon.web.data.TransactionRepository;
import org.zlogic.vogon.web.data.UserRepository;

/**
 * Class for pre-populating the database with some simple test data
 *
 * @author Dmitry Zolotukhin [zlogic@gmail.com]
 */
@Service
@Transactional
public class Prepopupate {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	/**
	 * Parses a date in JSON format
	 *
	 * @param date the date string to parse
	 * @return the parsed date
	 */
	public Date parseJSONDate(String date) {
		try {
			return new SimpleDateFormat("yyyy-MM-dd").parse(date); //NOI18N
		} catch (ParseException ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * Prepopulate the database with default test data
	 */
	public void prepopulate() {
		PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
		VogonUser user01 = new VogonUser("user01", passwordEncoder.encode("mypassword"));
		VogonUser user02 = new VogonUser("user02", passwordEncoder.encode("mypassword2"));
		userRepository.saveAll(Arrays.asList(user01, user02));

		FinanceAccount account1 = new FinanceAccount(user01, "test account 1", Currency.getInstance("RUB"));
		account1.setIncludeInTotal(true);
		account1.setShowInList(true);
		FinanceAccount account2 = new FinanceAccount(user01, "test account 2", Currency.getInstance("EUR"));
		account2.setIncludeInTotal(true);
		account2.setShowInList(true);
		FinanceAccount account3 = new FinanceAccount(user02, "test account 3", Currency.getInstance("RUB"));
		account3.setIncludeInTotal(true);
		account3.setShowInList(true);
		accountRepository.saveAll(Arrays.asList(account1, account2, account3));

		FinanceTransaction transaction1 = new FinanceTransaction(user01, "test transaction 1", new String[]{"hello", "world"}, parseJSONDate("2014-02-17"), FinanceTransaction.Type.EXPENSEINCOME);
		FinanceTransaction transaction3 = new FinanceTransaction(user01, "test transaction 3", new String[]{}, parseJSONDate("2014-02-17"), FinanceTransaction.Type.TRANSFER);
		FinanceTransaction transaction2 = new FinanceTransaction(user01, "test transaction 2", new String[]{"magic", "hello"}, parseJSONDate("2015-01-07"), FinanceTransaction.Type.EXPENSEINCOME);
		FinanceTransaction transaction4 = new FinanceTransaction(user02, "test transaction 3", new String[]{}, parseJSONDate("2014-05-17"), FinanceTransaction.Type.EXPENSEINCOME);
		TransactionComponent component1 = new TransactionComponent(account1, transaction1, 42 * 100);
		TransactionComponent component2 = new TransactionComponent(account2, transaction1, 160 * 100);
		TransactionComponent component3 = new TransactionComponent(account2, transaction2, -314);
		TransactionComponent component4 = new TransactionComponent(account1, transaction2, 272);
		TransactionComponent component5 = new TransactionComponent(account3, transaction4, 100 * 100);
		transactionRepository.saveAll(Arrays.asList(transaction1, transaction3, transaction2, transaction4));
		accountRepository.saveAll(Arrays.asList(account1, account2, account3));
	}

	/**
	 * Prepopulate the database with default and additional test data
	 */
	public void prepopulateExtra() {
		prepopulate();

		VogonUser user01 = userRepository.findByUsernameIgnoreCase("user01");

		List<FinanceAccount> accounts = accountRepository.findAll();
		FinanceAccount account1 = accounts.get(0);
		FinanceAccount account2 = accounts.get(1);

		FinanceTransaction transaction4 = new FinanceTransaction(user01, "test transaction 4", null, parseJSONDate("2014-06-07"), FinanceTransaction.Type.TRANSFER);
		TransactionComponent component41 = new TransactionComponent(account1, transaction4, -144 * 100);
		TransactionComponent component42 = new TransactionComponent(account2, transaction4, 144 * 100);
		transactionRepository.save(transaction4);
		accountRepository.saveAll(Arrays.asList(account1, account2));
	}

	/**
	 * Clear everything from the database
	 */
	public void clear() {
		transactionRepository.deleteAll();
		accountRepository.deleteAll();
		userRepository.deleteAll();
	}
}
