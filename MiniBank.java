import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * MiniBank — integrated single-file reference implementation for OOP Practicals 1–14.
 * Requires JDK 17+. Save as MiniBank.java, then run:
 *   javac MiniBank.java
 *   java MiniBank
 *
 * Runtime data is written under ./minibank-data.
 * For a conventional packaged/JUnit project, split these top-level types into files
 * and add the required package declarations and JUnit 5 dependency.
 */
public class MiniBank {
    private static final BankInfo BANK = new BankInfo("MiniBank", "CHARUSAT Branch");
    private static final Scanner SCANNER = new Scanner(System.in);
    private static final Path DATA_DIR = Path.of("minibank-data");
    private static final Path ACCOUNTS_FILE = DATA_DIR.resolve("accounts.dat");
    private static final Path LOG_DIR = DATA_DIR.resolve("logs");
    private static final Path REPORT_FILE = DATA_DIR.resolve("end-of-day-report.txt");

    private static final AccountStore STORE = new AccountStore();
    private static final Repository<Account> REPOSITORY = new Repository<>();
    private static final BankService SERVICE = new BankService(REPOSITORY, STORE);

    public static void main(String[] args) {
        System.out.println("======================================");
        System.out.println(BANK.name() + " | " + BANK.branch());
        System.out.println("======================================");
        try {
            Files.createDirectories(DATA_DIR);
            Files.createDirectories(LOG_DIR);
            loadStartupState();
        } catch (IOException e) {
            System.err.println("Startup storage warning: " + e.getMessage());
        }

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt("Choose an option: ");
            try {
                switch (MenuOption.fromNumber(choice)) {
                    case OPEN_ACCOUNT -> openAccountFlow();
                    case DEPOSIT -> depositFlow();
                    case WITHDRAW -> withdrawFlow();
                    case TRANSFER -> transferFlow();
                    case STATEMENT -> statementFlow();
                    case LIST_ACCOUNTS -> listAccountsFlow();
                    case WORKING_HOURS -> System.out.println("Working hours: Monday–Friday, 9:30 AM–4:00 PM.");
                    case RUN_REPORT -> runReportFlow();
                    case EXIT -> {
                        saveState();
                        System.out.println("Thank you for banking with MiniBank. Goodbye!");
                        running = false;
                    }
                }
            } catch (BankException e) {
                System.out.println("Banking operation failed: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("File operation failed: " + e.getMessage());
            } catch (RuntimeException e) {
                System.out.println("Invalid operation: " + e.getMessage());
            }
        }
    }

    private static void printMenu() {
        System.out.println("""

                -------- MiniBank Menu --------
                1. Open account
                2. Deposit
                3. Withdraw
                4. Transfer
                5. Account statement
                6. List accounts
                7. Working hours
                8. End-of-day report
                9. Exit
                --------------------------------""");
    }

    private static void openAccountFlow() throws BankException, IOException {
        String name = readLine("Customer full name: ");
        String email = readLine("Email: ");
        String mobile = readLine("10-digit mobile: ");
        if (!Validator.isValidEmail(email)) throw new InvalidAmountException("Invalid email address.");
        if (!Validator.isValidMobile(mobile)) throw new InvalidAmountException("Mobile must contain 10 digits.");
        Customer customer = new Customer(name, email, mobile);
        System.out.println("Customer created: " + customer);
        String owner = name;
        long opening = readLong("Opening balance (0 or more): ");
        if (opening < 0) throw new InvalidAmountException("Opening balance cannot be negative.");
        String type = readLine("Account type (SAVINGS/CURRENT/FD): ").trim().toUpperCase(Locale.ROOT);
        Account account = switch (type) {
            case "SAVINGS" -> new SavingsAccount(owner, opening, 500);
            case "CURRENT" -> new CurrentAccount(owner, opening, 5_000);
            case "FD" -> new FixedDepositAccount(owner, opening, LocalDateTime.now().plusDays(30));
            default -> throw new IllegalArgumentException("Choose SAVINGS, CURRENT, or FD.");
        };
        SERVICE.openAccount(account);
        System.out.println("Account opened successfully: " + account);
    }

    private static void depositFlow() throws BankException, IOException {
        String number = readLine("Account number: ");
        long amount = readLong("Amount to deposit: ");
        SERVICE.deposit(number, amount);
        System.out.println("Deposit successful.");
    }

    private static void withdrawFlow() throws BankException, IOException {
        String number = readLine("Account number: ");
        long amount = readLong("Amount to withdraw: ");
        SERVICE.withdraw(number, amount);
        System.out.println("Withdrawal successful.");
    }

    private static void transferFlow() throws BankException, IOException {
        String from = readLine("From account: ");
        String to = readLine("To account: ");
        long amount = readLong("Amount to transfer: ");
        SERVICE.transfer(from, to, amount);
        System.out.println("Transfer successful.");
    }

    private static void statementFlow() throws BankException {
        String number = readLine("Account number: ");
        System.out.println(SERVICE.statement(number));
    }

    private static void listAccountsFlow() {
        List<Account> accounts = SERVICE.listAccounts();
        if (accounts.isEmpty()) System.out.println("No accounts have been opened yet.");
        else accounts.forEach(System.out::println);
    }

    private static void runReportFlow() throws IOException {
        ReportGenerator.generate(LOG_DIR, REPORT_FILE);
        System.out.println(Files.readString(REPORT_FILE));
    }

    private static void loadStartupState() throws IOException {
        if (!Files.exists(ACCOUNTS_FILE)) return;
        try {
            Account[] loaded = StatePersister.load(ACCOUNTS_FILE);
            for (Account account : loaded) {
                if (account != null) {
                    STORE.add(account);
                    REPOSITORY.save(account);
                }
            }
            System.out.println("Loaded " + loaded.length + " account(s) from saved state.");
        } catch (ClassNotFoundException | InvalidClassException e) {
            System.out.println("Saved state could not be read; starting with an empty store.");
        }
    }

    private static void saveState() throws IOException {
        StatePersister.save(STORE.getAll().toArray(Account[]::new), ACCOUNTS_FILE);
        System.out.println("Account state saved.");
    }

    private static String readLine(String prompt) {
        System.out.print(prompt);
        return SCANNER.nextLine().trim();
    }

    private static int readInt(String prompt) {
        while (true) {
            try {
                return Integer.parseInt(readLine(prompt));
            } catch (NumberFormatException e) {
                System.out.println("Please enter a whole-number menu choice.");
            }
        }
    }

    private static long readLong(String prompt) {
        while (true) {
            try {
                return Long.parseLong(readLine(prompt));
            } catch (NumberFormatException e) {
                System.out.println("Please enter a whole number.");
            }
        }
    }
}

record BankInfo(String name, String branch) { }

enum MenuOption {
    OPEN_ACCOUNT(1), DEPOSIT(2), WITHDRAW(3), TRANSFER(4),
    STATEMENT(5), LIST_ACCOUNTS(6), WORKING_HOURS(7), RUN_REPORT(8), EXIT(9);

    private final int number;
    MenuOption(int number) { this.number = number; }

    static MenuOption fromNumber(int number) {
        for (MenuOption option : values()) if (option.number == number) return option;
        throw new IllegalArgumentException("Menu number must be between 1 and 9.");
    }
}

final class Customer implements Serializable {
    private static final long serialVersionUID = 1L;
    private static long customerCounter = 100;
    @Id private final String customerId;
    private String name;
    private String email;
    private String mobile;

    public Customer(String name, String email, String mobile) {
        this.customerId = generateCustomerId();
        this.name = Objects.requireNonNull(name).trim();
        this.email = Objects.requireNonNull(email).trim();
        this.mobile = Objects.requireNonNull(mobile).trim();
    }

    private static synchronized String generateCustomerId() {
        return "CUST" + (++customerCounter);
    }

    public String getCustomerId() { return customerId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getMobile() { return mobile; }

    @Override public String toString() {
        return customerId + " | " + name + " | " + email + " | " + mobile;
    }
}

interface Transactable {
    void deposit(long amount) throws InvalidAmountException;
    boolean withdraw(long amount) throws BankException;
}

interface InterestBearing {
    default double yearlyInterest() {
        if (this instanceof Account account) {
            return account.getBalance() * account.interestRate() / 100.0;
        }
        return 0.0;
    }
    default double monthlyInterest() { return yearlyInterest() / 12.0; }
}

@FunctionalInterface
interface WithdrawRule {
    boolean allow(Account account, long amount);
}

interface Premium { }

abstract class Account implements Transactable, InterestBearing, Comparable<Account>, Serializable {
    private static final long serialVersionUID = 1L;
    private static final AtomicLong ACCOUNT_COUNTER = new AtomicLong(0);
    @Id private final String accountNumber;
    @MaxLength(80) private String ownerName;
    @Positive private long balance;
    private boolean active = true;

    protected Account(String ownerName, long openingBalance) {
        this("AC" + String.format("%04d", ACCOUNT_COUNTER.incrementAndGet()), ownerName, openingBalance);
    }

    // Explicit-number constructor is useful for loading/testing and equality demonstrations.
    protected Account(String accountNumber, String ownerName, long openingBalance) {
        if (accountNumber == null || accountNumber.isBlank()) throw new IllegalArgumentException("Account number is required.");
        if (ownerName == null || ownerName.isBlank()) throw new IllegalArgumentException("Owner name is required.");
        if (openingBalance < 0) throw new IllegalArgumentException("Opening balance cannot be negative.");
        this.accountNumber = accountNumber;
        // Keep generated account numbers unique after loading previously saved accounts.
        if (accountNumber.matches("AC[0-9]+")) {
            try {
                ACCOUNT_COUNTER.accumulateAndGet(Long.parseLong(accountNumber.substring(2)), Math::max);
            } catch (NumberFormatException ignored) {
                // A non-standard or very large account number does not affect generation.
            }
        }
        this.ownerName = ownerName.trim();
        this.balance = openingBalance;
    }

    public String getAccountNumber() { return accountNumber; }
    public String getOwnerName() { return ownerName; }
    public synchronized long getBalance() { return balance; }
    public synchronized boolean isActive() { return active; }
    public synchronized void setActive(boolean active) { this.active = active; }

    @Override public synchronized void deposit(long amount) throws InvalidAmountException {
        if (amount <= 0) throw new InvalidAmountException("Deposit amount must be positive.");
        if (!active) throw new InvalidAmountException("Account is inactive.");
        balance = Math.addExact(balance, amount);
    }

    @Override public synchronized boolean withdraw(long amount) throws BankException {
        if (amount <= 0) throw new InvalidAmountException("Withdrawal amount must be positive.");
        if (!active) throw new AccountNotFoundException("Account is inactive.");
        if (!canWithdraw(amount)) {
            long shortfall = Math.max(0L, amount - (balance + permittedOverdraft()));
            throw new InsufficientFundsException(shortfall, "Insufficient funds; short by " + shortfall + ".");
        }
        balance -= amount;
        return true;
    }

    protected long permittedOverdraft() { return 0L; }
    public abstract double interestRate();
    public abstract boolean canWithdraw(long amount);

    protected synchronized void creditFromTransfer(long amount) throws InvalidAmountException {
        deposit(amount);
    }

    public String statement() {
        return """
                -------- MINI BANK STATEMENT --------
                Account number : %s
                Owner          : %s
                Balance        : %d
                Account active : %s
                Interest rate  : %.2f%%
                Yearly interest: %.2f
                -------------------------------------""".formatted(
                accountNumber, ownerName, getBalance(), isActive(), interestRate(), yearlyInterest());
    }

    @Override public int compareTo(Account other) {
        return accountNumber.compareTo(other.accountNumber);
    }

    @Override public boolean equals(Object obj) {
        return obj instanceof Account other && accountNumber.equals(other.accountNumber);
    }

    @Override public int hashCode() { return accountNumber.hashCode(); }

    @Override public String toString() {
        return "%s | %s | %d | active=%s | rate=%.1f%%".formatted(
                accountNumber, ownerName, getBalance(), isActive(), interestRate());
    }

    public static Account sampleForEquality(String number, String owner, long balance) {
        return new SavingsAccount(number, owner, balance, 0);
    }
}

final class SavingsAccount extends Account implements Premium {
    private static final long serialVersionUID = 1L;
    private final long minBalance;

    public SavingsAccount(String ownerName, long openingBalance, long minBalance) {
        super(ownerName, openingBalance);
        this.minBalance = Math.max(0, minBalance);
    }

    public SavingsAccount(String accountNumber, String ownerName, long openingBalance, long minBalance) {
        super(accountNumber, ownerName, openingBalance);
        this.minBalance = Math.max(0, minBalance);
    }

    public long getMinBalance() { return minBalance; }
    @Override public double interestRate() { return 4.0; }
    @Override public boolean canWithdraw(long amount) {
        return amount > 0 && getBalance() - amount >= minBalance;
    }
}

final class CurrentAccount extends Account {
    private static final long serialVersionUID = 1L;
    private final long overdraftLimit;

    public CurrentAccount(String ownerName, long openingBalance, long overdraftLimit) {
        super(ownerName, openingBalance);
        this.overdraftLimit = Math.max(0, overdraftLimit);
    }

    public long getOverdraftLimit() { return overdraftLimit; }
    @Override public double interestRate() { return 0.0; }
    @Override protected long permittedOverdraft() { return overdraftLimit; }
    @Override public boolean canWithdraw(long amount) {
        return amount > 0 && getBalance() - amount >= -overdraftLimit;
    }
}

final class FixedDepositAccount extends Account {
    private static final long serialVersionUID = 1L;
    private final LocalDateTime maturityDate;

    public FixedDepositAccount(String ownerName, long openingBalance, LocalDateTime maturityDate) {
        super(ownerName, openingBalance);
        this.maturityDate = Objects.requireNonNull(maturityDate);
    }

    public LocalDateTime getMaturityDate() { return maturityDate; }
    @Override public double interestRate() { return 7.0; }
    @Override public boolean canWithdraw(long amount) {
        return LocalDateTime.now().isAfter(maturityDate) && amount > 0 && getBalance() >= amount;
    }
}

class BankException extends Exception {
    private static final long serialVersionUID = 1L;
    public BankException(String message) { super(message); }
}

final class InsufficientFundsException extends BankException {
    private static final long serialVersionUID = 1L;
    private final long shortfall;
    public InsufficientFundsException(long shortfall, String message) {
        super(message);
        this.shortfall = shortfall;
    }
    public long getShortfall() { return shortfall; }
}

final class AccountNotFoundException extends BankException {
    private static final long serialVersionUID = 1L;
    public AccountNotFoundException(String message) { super(message); }
}

final class InvalidAmountException extends BankException {
    private static final long serialVersionUID = 1L;
    public InvalidAmountException(String message) { super(message); }
}

final class DailyLimitExceededException extends BankException {
    private static final long serialVersionUID = 1L;
    public DailyLimitExceededException(String message) { super(message); }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface Id { }

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface Positive {
    String message() default "must be > 0";
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface MaxLength {
    int value();
}

final class Validator {
    private static final Pattern MOBILE = Pattern.compile("^[6-9][0-9]{9}$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PAN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");
    private static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
    private static final Pattern AMOUNT = Pattern.compile("^[1-9][0-9]*$");

    private Validator() { }
    public static boolean isValidMobile(String value) { return value != null && MOBILE.matcher(value).matches(); }
    public static boolean isValidEmail(String value) { return value != null && EMAIL.matcher(value).matches(); }
    public static boolean isValidPan(String value) { return value != null && PAN.matcher(value).matches(); }
    public static boolean isValidIfsc(String value) { return value != null && IFSC.matcher(value).matches(); }
    public static boolean isValidAmount(String value) { return value != null && AMOUNT.matcher(value).matches(); }
}

enum TransactionType { DEPOSIT, WITHDRAW, TRANSFER }
record Command(TransactionType type, String accountNumber, long amount) { }

final class CommandParser {
    private CommandParser() { }
    public static Command parse(String line) {
        if (line == null) throw new IllegalArgumentException("Command cannot be null.");
        String[] parts = line.trim().split("\\s+");
        if (parts.length != 3) throw new IllegalArgumentException("Command must have exactly 3 parts: TYPE ACCOUNT AMOUNT.");
        TransactionType type = TransactionType.valueOf(parts[0].toUpperCase(Locale.ROOT));
        if (!Validator.isValidAmount(parts[2])) throw new IllegalArgumentException("Amount must be a positive whole number.");
        return new Command(type, parts[1], Long.parseLong(parts[2]));
    }
}

final class StatementFormatter {
    private StatementFormatter() { }
    public static String buildStatement(Account account) {
        return new StringBuilder()
                .append("MiniBank Statement\n")
                .append("Account: ").append(account.getAccountNumber()).append('\n')
                .append("Owner: ").append(account.getOwnerName()).append('\n')
                .append("Balance: ").append(account.getBalance()).append('\n')
                .append("Active: ").append(account.isActive()).append('\n')
                .append("Interest rate: ").append(account.interestRate()).append("%\n")
                .toString();
    }
}

final class AnnotationValidator {
    private AnnotationValidator() { }
    public static String[] validate(Object obj) {
        if (obj == null) return new String[]{"Object cannot be null"};
        List<String> errors = new ArrayList<>();
        for (Field field : obj.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (field.isAnnotationPresent(Positive.class) && value instanceof Number number && number.longValue() <= 0) {
                    errors.add(field.getName() + ": " + field.getAnnotation(Positive.class).message());
                }
                if (field.isAnnotationPresent(MaxLength.class) && value instanceof String string
                        && string.length() > field.getAnnotation(MaxLength.class).value()) {
                    errors.add(field.getName() + ": maximum length is " + field.getAnnotation(MaxLength.class).value());
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                errors.add(field.getName() + ": could not validate field");
            }
        }
        return errors.toArray(String[]::new);
    }
}

final class AccountStore {
    private final ConcurrentHashMap<String, Account> accounts = new ConcurrentHashMap<>();
    private final List<String> transactionHistory = new CopyOnWriteArrayList<>();

    public void add(Account account) {
        Objects.requireNonNull(account);
        Account previous = accounts.putIfAbsent(account.getAccountNumber(), account);
        if (previous != null && previous != account) throw new IllegalArgumentException("Duplicate account number.");
    }
    public Account get(String accountNumber) { return accounts.get(accountNumber); }
    public List<Account> getAll() { return new ArrayList<>(accounts.values()); }
    public List<String> getTransactionHistory() { return new ArrayList<>(transactionHistory); }
    public void record(String line) { transactionHistory.add(line); }
    public List<Account> sortedByBalance() {
        return accounts.values().stream()
                .sorted(Comparator.comparingLong(Account::getBalance).reversed())
                .collect(Collectors.toCollection(ArrayList::new));
    }
    public List<Account> aboveBalance(long value) {
        return accounts.values().stream().filter(a -> a.getBalance() > value).toList();
    }
}

final class Repository<T> {
    private final ConcurrentHashMap<Object, T> items = new ConcurrentHashMap<>();

    public void save(T item) {
        Objects.requireNonNull(item);
        Object id = readId(item);
        if (id == null) throw new IllegalArgumentException("Object has no non-null @Id field.");
        items.put(id, item);
    }
    public T findById(Object id) { return items.get(id); }
    public List<T> findAll() { return new ArrayList<>(items.values()); }
    public boolean deleteById(Object id) { return items.remove(id) != null; }
    public int count() { return items.size(); }

    private Object readId(T item) {
        Class<?> type = item.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    try {
                        field.setAccessible(true);
                        return field.get(item);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Unable to read @Id field.", e);
                    }
                }
            }
            type = type.getSuperclass();
        }
        throw new IllegalArgumentException("Type " + item.getClass().getSimpleName() + " has no @Id field.");
    }
}

final class BankService {
    private final Repository<Account> repository;
    private final AccountStore store;
    private final WithdrawRule withdrawRule = (account, amount) -> account != null && account.canWithdraw(amount);

    BankService(Repository<Account> repository, AccountStore store) {
        this.repository = repository;
        this.store = store;
    }

    public synchronized void openAccount(Account account) {
        if (account == null) throw new IllegalArgumentException("Account is required.");
        store.add(account);
        repository.save(account);
    }

    public void deposit(String accountNumber, long amount) throws BankException, IOException {
        Account account = requireAccount(accountNumber);
        account.deposit(amount);
        log("DEPOSIT", accountNumber, amount);
    }

    public void withdraw(String accountNumber, long amount) throws BankException, IOException {
        Account account = requireAccount(accountNumber);
        if (!withdrawRule.allow(account, amount)) throw new InsufficientFundsException(
                Math.max(0, amount - account.getBalance()), "Withdrawal is not allowed by account rules.");
        account.withdraw(amount);
        log("WITHDRAW", accountNumber, amount);
    }

    public void transfer(String fromNumber, String toNumber, long amount) throws BankException, IOException {
        if (amount <= 0) throw new InvalidAmountException("Transfer amount must be positive.");
        Account from = requireAccount(fromNumber);
        Account to = requireAccount(toNumber);
        if (from.equals(to)) throw new InvalidAmountException("Source and destination must differ.");
        Account first = from.getAccountNumber().compareTo(to.getAccountNumber()) < 0 ? from : to;
        Account second = first == from ? to : from;
        synchronized (first) {
            synchronized (second) {
                from.withdraw(amount);
                try {
                    to.deposit(amount);
                } catch (BankException | RuntimeException e) {
                    // Best-effort rollback to preserve balances if destination credit fails.
                    try { from.deposit(amount); } catch (BankException ignored) { }
                    throw e;
                } finally {
                    // Transaction boundary hook: intentionally no-op for this single-file demo.
                }
            }
        }
        log("TRANSFER", fromNumber + "->" + toNumber, amount);
    }

    public String statement(String accountNumber) throws AccountNotFoundException {
        return StatementFormatter.buildStatement(requireAccount(accountNumber));
    }

    public List<Account> listAccounts() { return store.sortedByBalance(); }

    private Account requireAccount(String accountNumber) throws AccountNotFoundException {
        Account account = store.get(accountNumber);
        if (account == null) throw new AccountNotFoundException("No account found for " + accountNumber + ".");
        return account;
    }

    private void log(String type, String accountNumber, long amount) throws IOException {
        String line = "%s %s %d | %s".formatted(type, accountNumber, amount, LocalDateTime.now());
        store.record(line);
        TransactionLog.append(MiniBankLogPaths.LOG_FILE, line);
    }
}

final class MiniBankLogPaths {
    static final Path LOG_FILE = Path.of("minibank-data", "logs", "transactions.log");
    private MiniBankLogPaths() { }
}

final class TransactionProcessor implements AutoCloseable {
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final ConcurrentHashMap<String, Account> sharedAccounts = new ConcurrentHashMap<>();

    public void register(Account account) { sharedAccounts.put(account.getAccountNumber(), account); }
    public void submit(Runnable task) { executor.execute(task); }
    public Map<String, Account> sharedAccounts() { return sharedAccounts; }

    public void stop() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    @Override public void close() { stop(); }
}

final class AccountWorker implements Runnable {
    private final Account account;
    private final int times;
    private final long amount;

    AccountWorker(Account account, int times, long amount) {
        this.account = account;
        this.times = times;
        this.amount = amount;
    }

    @Override public void run() {
        System.out.println(Thread.currentThread().getName() + " started.");
        for (int i = 0; i < times; i++) {
            try { account.deposit(amount); }
            catch (InvalidAmountException e) { throw new IllegalArgumentException(e); }
        }
        System.out.println(Thread.currentThread().getName() + " finished.");
    }
}

final class StatePersister {
    private StatePersister() { }
    public static void save(Account[] accounts, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(file))) {
            out.writeObject(accounts);
        }
    }

    public static Account[] load(Path file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(file))) {
            Object object = in.readObject();
            if (!(object instanceof Account[] accounts)) throw new IOException("Saved data is not an Account array.");
            return accounts;
        }
    }
}

final class TransactionLog {
    private TransactionLog() { }
    public static void append(Path file, String line) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(file, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}

final class ReportGenerator {
    private ReportGenerator() { }
    public static String generate(Path logsFolder, Path reportFile) throws IOException {
        long deposits = 0;
        long withdrawals = 0;
        long transfers = 0;
        StringBuilder details = new StringBuilder();
        if (Files.isDirectory(logsFolder)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(logsFolder, "*.log")) {
                for (Path file : stream) {
                    if (!Files.isRegularFile(file) || Files.size(file) == 0) continue;
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    details.append(file.getFileName()).append(" | size=").append(attrs.size())
                            .append(" | modified=").append(attrs.lastModifiedTime()).append('\n');
                    try (BufferedReader reader = Files.newBufferedReader(file)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String[] parts = line.trim().split("\\s+");
                            if (parts.length < 3) continue;
                            try {
                                long amount = Long.parseLong(parts[2]);
                                switch (parts[0]) {
                                    case "DEPOSIT" -> deposits += amount;
                                    case "WITHDRAW" -> withdrawals += amount;
                                    case "TRANSFER" -> transfers += amount;
                                    default -> { }
                                }
                            } catch (NumberFormatException ignored) {
                                // Skip malformed lines.
                            }
                        }
                    }
                }
            }
        }
        String report = """
                ===== MiniBank End-of-Day Report =====
                Generated: %s
                Total deposits: %d
                Total withdrawals: %d
                Total transfer volume: %d
                Net deposits minus withdrawals: %d
                Log files:
                %s
                =======================================
                """.formatted(LocalDateTime.now(), deposits, withdrawals, transfers,
                deposits - withdrawals, details);
        Path parent = reportFile.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(reportFile, report, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return report;
    }
}

/** AutoCloseable example for try-with-resources (Practical 8). */
final class DemoResource implements AutoCloseable {
    @Override public void close() { System.out.println("Demo resource closed safely."); }
}

/** A simple bounded producer-consumer buffer using wait/notify (Practical 10). */
final class TransactionBuffer {
    private final Queue<Runnable> queue = new ArrayDeque<>();
    private final int capacity;

    TransactionBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Capacity must be positive.");
        this.capacity = capacity;
    }

    public synchronized void put(Runnable task) throws InterruptedException {
        while (queue.size() == capacity) wait();
        queue.add(task);
        notifyAll();
    }

    public synchronized Runnable take() throws InterruptedException {
        while (queue.isEmpty()) wait();
        Runnable task = queue.remove();
        notifyAll();
        return task;
    }
}

/*
 * Suggested JUnit 5 tests (place in MiniBankTest.java after splitting into a project):
 *
 * @Test void depositIncreasesBalance() throws Exception {
 *     Account a = new SavingsAccount("Test", 1000, 0);
 *     a.deposit(500);
 *     assertEquals(1500, a.getBalance());
 * }
 *
 * @Test void withdrawalOverAvailableFundsThrows() {
 *     Account a = new SavingsAccount("Test", 1000, 0);
 *     assertThrows(InsufficientFundsException.class, () -> a.withdraw(2000));
 * }
 *
 * @Test void concurrentDepositsAreSafe() throws Exception {
 *     Account a = new SavingsAccount("Test", 0, 0);
 *     List<Thread> threads = new ArrayList<>();
 *     for (int i = 0; i < 10; i++) {
 *         Thread t = new Thread(new AccountWorker(a, 1000, 1));
 *         threads.add(t); t.start();
 *     }
 *     for (Thread t : threads) t.join();
 *     assertEquals(10000, a.getBalance());
 * }
 */
