package pt.ulisboa.tecnico.softeng.bank.domain
import spock.lang.Specification
import org.junit.Assert
import pt.ulisboa.tecnico.softeng.bank.exception.BankException
import spock.lang.Unroll

class AccountWithdrawMethodSpockTest extends SpockRollbackTestAbstractClass {
	def bank
	def account

	@Override
	def populate4Test() {
		bank = new Bank('Money','BK01')
		Client client = new Client(bank,'António')

		account = new Account(bank,client)

		account.deposit(100)
	}

	@Unroll('Withdraw: #label')
	def 'success'() {
		when: 'when withdrawing 40'
		def reference= account.withdraw(amnt).getReference()

		then: 'success'
		balance == account.getBalance()
		Operation operation = bank.getOperation(reference)
		operation != null
		operation.getType() == Operation.Type.WITHDRAW
		operation.getAccount() == account
		amnt == operation.getValue()

		where:
		label              | amnt | balance
		'forty'            | 40   | 60
		'one amount'       | 1    | 99
		'equal to balance' | 100  | 0
	}

	@Unroll('Withdraw: #label')
	def 'throwing exception'() {
		when: 'when withdrawing an invalid amount'
		account.withdraw(amnt)

		then: 'throw an exception'
		thrown(BankException)

		where:
		amnt | label
		0    | 'zero amount'
		-20  | 'negative amount'
		101  | 'equal to balance plus one'
		150  | 'more than balance'
	}
}
