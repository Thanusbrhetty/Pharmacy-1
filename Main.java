import java.io.*;
import java.math.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

public class Main {
    private static final Path DATA = Path.of("pharmacy.dat");
    private static final Comparator<Medicine> EXPIRY = Comparator.comparing(Medicine::expiry).thenComparing(Medicine::id);
    private record Medicine(String id, String name, BigDecimal price, int stock, LocalDate expiry)
            implements Serializable {}
    private record Supplier(String id, String name, String phone, String address) implements Serializable {}
    private record Request(String customer, Map<String, Integer> items, BigDecimal discount) implements Serializable {}
    private record Line(Medicine before, Medicine after, int quantity, BigDecimal subtotal) implements Serializable {}
    private record Bill(long number, String customer, LocalDate date, List<Line> lines,
                        BigDecimal subtotal, BigDecimal discountRate, BigDecimal discount,
                        BigDecimal total) implements Serializable {}

    private static class State implements Serializable {
        private static final long serialVersionUID = 1L;
        private final ArrayList<Medicine> medicines = new ArrayList<>();
        private final LinkedList<Supplier> suppliers = new LinkedList<>();
        private final Queue<Request> requests = new ArrayDeque<>();
        private final Stack<Bill> sales = new Stack<>();
        private final ArrayList<Bill> voided = new ArrayList<>();
        private long nextBill = 1;
    }

    private final Scanner scanner = new Scanner(System.in);
    private final Map<String, Medicine> index = new HashMap<>();
    private final State state;

    private Main(State state) {
        this.state = state;
        state.medicines.forEach(m -> index.put(m.id(), m));
    }

    public static void main(String[] args) {
        try {
            State state = new State();
            if (!Files.notExists(DATA)) {
                try (var input = new ObjectInputStream(Files.newInputStream(DATA))) {
                    input.setObjectInputFilter(ObjectInputFilter.Config.createFilter("Main$*;java.base/*;!*"));
                    state = (State) input.readObject();
                }
            }
            new Main(state).run();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            System.err.println("Data file error: " + e.getMessage());
        } catch (NoSuchElementException e) {
            System.out.println("Input closed.");
        }
    }

    private void save() throws IOException {
        Path temp = Files.createTempFile(DATA.toAbsolutePath().getParent(), "pharmacy-", ".tmp");
        try {
            try (var output = new ObjectOutputStream(Files.newOutputStream(temp))) {
                output.writeObject(state);
            }
            Files.move(temp, DATA, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void run() throws IOException {
        while (true) {
            System.out.println("\nPHARMACY MANAGEMENT SYSTEM\n1. Medicine Stock Management\n"
                    + "2. Expiry Date Management\n3. Billing Management\n4. Supplier Management\n"
                    + "5. Medicine Search Engine\n0. Exit");
            switch (number("Choice: ", 0, 5)) {
                case 1 -> stock();
                case 2 -> expiry();
                case 3 -> billing();
                case 4 -> suppliers();
                case 5 -> search();
                case 0 -> { return; }
            }
        }
    }

    private void stock() throws IOException {
        System.out.println("1 Add | 2 Update | 3 Delete | 4 Medicine Search Engine"
                + " | 5 Sort | 6 Low stock | 7 Display | 0 Back");
        int choice = number("Choice: ", 0, 7);
        List<Medicine> medicines = state.medicines;
        if (choice >= 1 && choice <= 3) {
            String id = readId();
            Medicine old = index.get(id);
            if (choice != 1 && old == null) {
                System.out.println("Medicine not found.");
                return;
            }
            if (choice == 1 && old != null) {
                System.out.println("ID already exists.");
                return;
            }
            if (choice == 3) {
                medicines.remove(old);
                index.remove(id);
            } else {
                Medicine medicine = new Medicine(id, required("Name: "), decimal("Price: ", new BigDecimal("0.01"), null),
                        number("Quantity: ", 0, Integer.MAX_VALUE), validate("Expiry (YYYY-MM-DD): ", LocalDate::parse));
                if (old == null) medicines.add(medicine);
                else replace(old, medicine);
                index.put(id, medicine);
            }
            save();
            System.out.println(switch (choice) {
                case 1 -> "Medicine added.";
                case 2 -> "Medicine updated.";
                default -> "Medicine deleted.";
            });
        } else if (choice == 4) {
            search();
        } else if (choice == 5) {
            System.out.println("1 Name | 2 Price | 3 Quantity | 4 Expiry");
            Comparator<Medicine> order = switch (number("Sort by: ", 1, 4)) {
                case 1 -> Comparator.comparing(Medicine::name, String.CASE_INSENSITIVE_ORDER);
                case 2 -> Comparator.comparing(Medicine::price);
                case 3 -> Comparator.comparingInt(Medicine::stock);
                default -> EXPIRY;
            };
            show(medicines.stream().sorted(order).toList());
        } else if (choice == 6) {
            show(medicines.stream().filter(m -> m.stock() <= 5).toList());
        } else if (choice == 7) show(medicines);
    }

    private void search() {
        System.out.println("\n=== MEDICINE SEARCH ENGINE ===");
        System.out.println("1 Search by ID (HashMap) | 2 Search by name (Linear) | 3 Search by ID (Binary) | 0 Back");
        List<Medicine> matches = new ArrayList<>();
        switch (number("Choose a search method: ", 0, 3)) {
            case 1 -> {
                Medicine medicine = index.get(readId());
                if (medicine != null) {
                    matches.add(medicine);
                }
            }
            case 2 -> {
                String query = required("Enter all or part of the medicine name: ").toLowerCase(Locale.ROOT);
                for (Medicine medicine : state.medicines) {
                    if (medicine.name().toLowerCase(Locale.ROOT).contains(query)) {
                        matches.add(medicine);
                    }
                }
            }
            case 3 -> {
                String id = readId();
                // Binary search needs a sorted list of IDs.
                List<String> ids = new ArrayList<>(index.keySet());
                Collections.sort(ids);
                int position = Collections.binarySearch(ids, id);
                if (position >= 0) {
                    matches.add(index.get(ids.get(position)));
                }
            }
            default -> { return; }
        }
        System.out.printf("%nSearch results (%d found)%n", matches.size());
        if (matches.isEmpty()) System.out.println("No matching medicines.");
        for (Medicine m : matches) {
            System.out.printf("%s | %s | Price: %.2f | Stock: %d | Expiry: %s%n",
                    m.id(), m.name(), m.price(), m.stock(), m.expiry());
        }
    }

    private void expiry() {
        System.out.println("1 Expired | 2 Due within 30 days | 3 Earliest first | 0 Back");
        int choice = number("Choice: ", 0, 3);
        if (choice == 0) return;
        LocalDate today = LocalDate.now();
        Queue<Medicine> due = new PriorityQueue<>(EXPIRY);
        for (Medicine medicine : state.medicines) {
            boolean expired = medicine.expiry().isBefore(today);
            if (choice == 3 || (choice == 1 && expired)
                    || (choice == 2 && !expired && !medicine.expiry().isAfter(today.plusDays(30)))) {
                due.offer(medicine);
            }
        }
        if (due.isEmpty()) System.out.println("No matching records.");
        while (!due.isEmpty()) System.out.println(due.poll());
    }

    private void suppliers() throws IOException {
        System.out.println("1 Add | 2 Update | 3 Delete | 4 Search | 5 Display | 0 Back");
        int choice = number("Choice: ", 0, 5);
        if (choice == 0) return;
        if (choice == 5) { show(state.suppliers); return; }
        String id = readId();
        Supplier old = state.suppliers.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
        if (choice == 4) { show(old == null ? List.of() : List.of(old)); return; }
        if ((choice == 1) == (old != null)) {
            System.out.println(old == null ? "Supplier not found." : "ID already exists.");
            return;
        }
        if (choice == 3) state.suppliers.remove(old);
        else {
            Supplier supplier = new Supplier(id, required("Name: "), required("Phone: "), required("Address: "));
            if (old == null) state.suppliers.add(supplier);
            else state.suppliers.set(state.suppliers.indexOf(old), supplier);
        }
        save();
        System.out.println(switch (choice) {
            case 1 -> "Supplier added.";
            case 2 -> "Supplier updated.";
            default -> "Supplier deleted.";
        });
    }

    private void billing() throws IOException {
        System.out.println("1 Queue request | 2 Bill next | 3 History | 4 Undo | 5 View queue | 6 Cancel next | 0 Back");
        switch (number("Choice: ", 0, 6)) {
            case 1 -> queueRequest();
            case 2 -> billNext();
            case 3 -> {
                List<Bill> history = new ArrayList<>(state.sales);
                history.addAll(state.voided);
                history.sort(Comparator.comparingLong(Bill::number).reversed());
                if (history.isEmpty()) System.out.println("No bills.");
                history.forEach(b -> printBill(b, state.voided.contains(b) ? "VOIDED" : "PAID"));
            }
            case 4 -> undo();
            case 5 -> show(state.requests);
            case 6 -> {
                if (state.requests.poll() == null) System.out.println("Queue is empty.");
                else { save(); System.out.println("Request cancelled."); }
            }
            default -> { }
        }
    }

    private void queueRequest() throws IOException {
        String customer = required("Customer: ");
        Map<String, Integer> items = new LinkedHashMap<>();
        for (String id; !(id = input("Medicine ID (Enter to finish): ").toUpperCase(Locale.ROOT)).isEmpty();) {
            Medicine medicine = index.get(id);
            int used = items.getOrDefault(id, 0);
            if (sellable(medicine, used + 1L)) {
                items.merge(id, number("Quantity: ", 1, medicine.stock() - used), Integer::sum);
            } else System.out.println("Unavailable, expired, or insufficient stock: " + id);
        }
        if (items.isEmpty()) { System.out.println("No items selected."); return; }
        state.requests.offer(new Request(customer, items, decimal("Discount %: ", BigDecimal.ZERO, BigDecimal.valueOf(100))));
        save();
        System.out.println("Request queued.");
    }

    private void billNext() throws IOException {
        Request request = state.requests.peek();
        if (request == null) { System.out.println("Queue is empty."); return; }
        List<Line> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        // Validate every item before changing inventory.
        for (var item : request.items().entrySet()) {
            Medicine m = index.get(item.getKey());
            int quantity = item.getValue();
            if (!sellable(m, quantity)) {
                System.out.println("Billing blocked for " + item.getKey() + ". The request is still queued.");
                return;
            }
            BigDecimal amount = m.price().multiply(BigDecimal.valueOf(quantity));
            lines.add(new Line(m, new Medicine(m.id(), m.name(), m.price(), m.stock() - quantity, m.expiry()), quantity, amount));
            subtotal = subtotal.add(amount);
        }
        BigDecimal discount = subtotal.multiply(request.discount()).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        Bill bill = new Bill(state.nextBill++, request.customer(), LocalDate.now(), lines, subtotal,
                request.discount(), discount, subtotal.subtract(discount));
        lines.forEach(line -> replace(line.before(), line.after()));
        state.requests.poll();
        state.sales.push(bill);
        save();
        printBill(bill, "PAID");
        lines.stream().map(Line::after).filter(m -> m.stock() <= 5)
                .forEach(m -> System.out.println("Low stock: " + m.name() + " (" + m.stock() + ")"));
    }

    private void undo() throws IOException {
        if (state.sales.empty()) { System.out.println("No bill to undo."); return; }
        Bill bill = state.sales.peek();
        // The same record must still be in stock, even after a restart.
        if (bill.lines().stream().anyMatch(line -> index.get(line.after().id()) != line.after())) {
            System.out.println("Undo blocked because inventory changed.");
            return;
        }
        if (!input("Void bill #" + bill.number() + " and restore stock? (yes/no): ").equalsIgnoreCase("yes")) return;
        bill.lines().forEach(line -> replace(line.after(), line.before()));
        state.voided.add(state.sales.pop());
        save();
        System.out.println("Bill #" + bill.number() + " voided. Stock restored.");
    }

    private void replace(Medicine old, Medicine replacement) {
        state.medicines.set(state.medicines.indexOf(old), replacement);
        index.put(replacement.id(), replacement);
    }

    private boolean sellable(Medicine m, long quantity) {
        return m != null && quantity > 0 && quantity <= m.stock() && !m.expiry().isBefore(LocalDate.now());
    }

    private void printBill(Bill bill, String status) {
        System.out.printf("Bill #%d | %s | %s | %s%n", bill.number(), status, bill.date(), bill.customer());
        for (Line line : bill.lines()) {
            System.out.printf("%s: %d x %.2f = %.2f%n", line.before().name(), line.quantity(), line.before().price(), line.subtotal());
        }
        System.out.printf("Subtotal: %.2f%nDiscount (%s%%): %.2f%nTotal: %.2f%n",
                bill.subtotal(), bill.discountRate(), bill.discount(), bill.total());
    }

    private void show(Collection<?> records) {
        if (records.isEmpty()) System.out.println("No matching records.");
        records.forEach(System.out::println);
    }

    private String readId() { return required("ID: ").toUpperCase(Locale.ROOT); }
    private String input(String prompt) { System.out.print(prompt); return scanner.nextLine().trim(); }
    private String required(String prompt) { return validate(prompt, s -> s.isEmpty() ? null : s); }

    private int number(String prompt, int min, int max) {
        return validate(prompt, text -> {
            int value = Integer.parseInt(text);
            return value >= min && value <= max ? value : null;
        });
    }

    private BigDecimal decimal(String prompt, BigDecimal min, BigDecimal max) {
        return validate(prompt, text -> {
            if (!text.matches("[0-9]+(\\.[0-9]{1,2})?")) return null;
            BigDecimal value = new BigDecimal(text);
            return value.compareTo(min) >= 0 && (max == null || value.compareTo(max) <= 0) ? value : null;
        });
    }

    private <T> T validate(String prompt, Function<String, T> parse) {
        while (true) {
            String text = input(prompt);
            try {
                T result = parse.apply(text);
                if (result != null) return result;
            } catch (NumberFormatException | java.time.format.DateTimeParseException e) {
                // Ask again for malformed numbers or dates.
            }
            System.out.println("Invalid input. Try again.");
        }
    }
}
