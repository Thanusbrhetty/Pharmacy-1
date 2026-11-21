# Pharmacy-1
A console-based Pharmacy Management System developed using Java and Data Structures &amp; Algorithms (DSA). It manages medicine stock, expiry alerts, billing, supplier records, and transaction history using ArrayList, LinkedList, HashMap, Queue, PriorityQueue, and Stack. Built as a beginner-friendly academic project using only one or two Java files.

## Run in VS Code

Open the repository folder, then run the following in the integrated terminal with JDK 17 or later installed.

```powershell
java Main.java
```

Start the program from the same folder each time so it can restore the local `pharmacy.dat` file.

## Try the search engine

1. From the main menu select 1, then 1 to add a medicine.
2. Enter ID M101, name Paracetamol, price 12.50, quantity 6, and expiry 2099-12-31.
3. Select 5 from the main menu and choose 2 for a linear name search. Enter para to find Paracetamol.
4. Try search option 1 for HashMap lookup or 3 for binary search, using M101. An unknown ID should return zero results.

## Data structures

| Structure | Use |
| --- | --- |
| ArrayList | Medicine stock and sorted copies |
| LinkedList | Supplier records |
| HashMap | Average constant-time medicine ID lookup |
| Queue | First-in, first-out billing requests |
| PriorityQueue | Expiry reports in earliest-date order |
| Stack | Latest active sale first when undoing a bill |

Name search scans the medicine list in O(n) time. Binary search takes O(log n) after sorting the IDs, which takes O(n log n) for that menu option.
