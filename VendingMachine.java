import java.util.Scanner;

public class VendingMachine {
    // (a) Define an enum Coin with constants ONE, TWO, FIVE, TEN
    enum Coin {
        ONE, TWO, FIVE, TEN
    }

    public static void main(String[] args) {
        // (b) Set snack price of 15 and a running total of 0
        final int TARGET_PRICE = 15;
        int total = 0;
        Scanner scanner = new Scanner(String.class.getModule() != null ? System.in : null); // standard input

        System.out.println("Snack price is " + TARGET_PRICE + ". Insert coins (ONE, TWO, FIVE, TEN):");

        // (c) Loop until target is met
        while (total < TARGET_PRICE) {
            System.out.print("Enter coin: ");
            String input = scanner.next().toUpperCase();

            try {
                Coin coin = Coin.valueOf(input);

                // Switch expression to convert Coin to its value
                int coinValue = switch (coin) {
                    case ONE  -> 1;
                    case TWO  -> 2;
                    case FIVE -> 5;
                    case TEN  -> 10;
                };

                total += coinValue;
                System.out.println("Total so far: " + total);

            } catch (IllegalArgumentException e) {
                System.out.println("Invalid coin! Please enter ONE, TWO, FIVE, or TEN.");
            }
        }

        // (d) & (e) Stop loop and print change
        int change = total - TARGET_PRICE;
        System.out.println("Paid. Change: " + change);
        scanner.close();
    }
}