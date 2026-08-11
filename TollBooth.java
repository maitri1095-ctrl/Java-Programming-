import java.util.Scanner;

public class TollBooth {
    // (a) Define a record Vehicle
    record Vehicle(String number, String type) {}

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // (b) Running toll total and counters
        int totalToll = 0;
        int bikeCount = 0;
        int carCount = 0;
        int truckCount = 0;

        // (c) Loop until user types "done"
        while (true) {
            System.out.print("Enter vehicle number (or 'done' to finish): ");
            String number = scanner.next();
            if (number.equalsIgnoreCase("done")) {
                break;
            }

            System.out.print("Enter vehicle type (bike, car, truck): ");
            String type = scanner.next().toLowerCase();

            Vehicle vehicle = new Vehicle(number, type);

            // Switch expression on vehicle type
            int toll = switch (vehicle.type()) {
                case "bike" -> {
                    bikeCount++;
                    yield 20;
                }
                case "car" -> {
                    carCount++;
                    yield 50;
                }
                case "truck" -> {
                    truckCount++;
                    yield 150;
                }
                default -> {
                    System.out.println("Unknown vehicle type! No toll added.");
                    yield 0;
                }
            };

            totalToll += toll;
        }

        // (d) Print total toll and find the highest count
        System.out.println("\nTotal toll: " + totalToll);

        String mostFrequent = "None";
        int maxCount = 0;

        if (bikeCount > maxCount) { maxCount = bikeCount; mostFrequent = "bike"; }
        if (carCount > maxCount) { maxCount = carCount; mostFrequent = "car"; }
        if (truckCount > maxCount) { maxCount = truckCount; mostFrequent = "truck"; }

        System.out.println("Most frequent: " + mostFrequent);
        scanner.close();
    }
}