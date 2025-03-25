import java.sql.*;
import java.util.List;
import java.util.Scanner;

public class App {
private enum USER_TYPE { // User type, determined on login
    MEMBER,
    EMPLOYEE,
    CURATOR
}
// Primary URL used to make a connection to the database, requires credentials to be inserted
private static final String BASE_CONNECTION_URL = "jdbc:sqlserver://cxp-sql-03\\whc44;database=Library;encrypt=true;trustServerCertificate=true;loginTimeout=15";
// Columns for all attributes we want to retrieve from our queries per table
private static final List<String> BOOK_COLUMNS = List.of("bookID","title", "ISBN", "edition", "publicationDate", "publisher", "copyrightYear");
private static final List<String> MEMBER_COLUMNS = List.of("memberID", "memFirstName", "memLastName", "memdob", "memdor");
private static final List<String> MEMBER_COPY_COLUMNS = List.of("memberID", "copyID", "memCopyStatus", "createdDate", "expiryDate");
private static final List<String> AUTHOR_COLUMNS = List.of("authorID", "firstName", "lastName", "dob", "status");
private static int memberID = -1; // Member ID, set on login
private static USER_TYPE userType = USER_TYPE.MEMBER;

// Method to create a connection URL with an inserted user and password
private static String getConnectionUrl(String user, String password) {
    return BASE_CONNECTION_URL + ";user=" + user + ";password=" + password;
}

// Utility method to print a line to the console, used for UI formatting
public static void printLine() {
    System.out.println("------------------------------");
}

// Main method to run the application
public static void main(String[] args) throws Exception {
    // Prompt user for database login
    Scanner scanner = new Scanner(System.in);
    System.out.println("Enter database login:");
    String user = scanner.nextLine();
    System.out.println("Enter database password:");
    String password = scanner.nextLine();
    String connectionUrl = getConnectionUrl(user, password);
    // Determine what type of user is logging in
    // Because each login is given separate permissions in the database, this ensures they can only run commands they have access to
    if (user.equals("member_login")) {
        // Right now, creating individual logins for users was not in our use cases, so we just prompt for member ID
        System.out.println("Enter member ID:");
        memberID = Integer.parseInt(scanner.nextLine());
    } else if (user.equals("employee_login")) {
        userType = USER_TYPE.EMPLOYEE;
    } else if (user.equals("curator_login")) {
        userType = USER_TYPE.CURATOR;
    }
    // Connect to database
    try (Connection connection = DriverManager.getConnection(connectionUrl);
    ) {
        // This allows us to rollback uncommitted transactions if an error occurs
        // parseCommand will return a boolean indicating the success status of the command
        // If the command is successful, we commit the transaction with connection.commit()
        // Otherwise, we rollback the transaction with connection.rollback()
        connection.setAutoCommit(false);
        System.out.println("Successfully connected!");
        while (true) { // Main loop for user input
            System.out.println("Enter command:");
            printLine();
            printAvailableCommands();
            printLine();
            String command = scanner.nextLine();
            // Exit command to log out of the database
            if (command.equals("exit") || command.equals("logout")) {
                System.out.println("Logged out of library database");
                break;
            }
            // Commit or rollback transaction based on command success
            if (parseCommand(command, connectionUrl)) {
                connection.commit();
            } else {
                connection.rollback();
                System.out.println("This transaction failed and was rolled back.");
            }
        }
    }
}

// Method to execute a stored procedure with the given parameter and procedure name
private static ResultSet executeProcedure(String procedureName, String parameter, String connectionUrl, List<String> columns) {
    try (Connection connection = DriverManager.getConnection(connectionUrl);
         CallableStatement callableStatement = connection.prepareCall("{call " + procedureName + "(?)}")) {
        callableStatement.setString(1, parameter);
        boolean hasResultSet = callableStatement.execute();
        if (hasResultSet) {
            try (ResultSet resultSet = callableStatement.getResultSet()) {
                int count = 1;
                while (resultSet.next()) {
                    System.out.println("Result " + count + ":");
                    printLine();
                    for (String column : columns) {
                        try {
                            System.out.println(column + ": " + resultSet.getString(column));
                        } catch (SQLException e) {
                            System.out.println(column + ": N/A");
                        }
                        if (column.equals("expiryDate")) { // Handle overdue checking at query time
                            try {
                                Date expiryDate = resultSet.getDate(column);
                                if (expiryDate.before(new Date(System.currentTimeMillis()))) {
                                    System.out.println("(OVERDUE!)");
                                }
                            } catch (SQLException e) {
                                System.out.println(e.getMessage());
                            }
                        }
                    }
                    count++;
                    printLine();
                }
                return resultSet;
            }
        }
    } catch (SQLException e) {
        System.out.println(e.getMessage());
        return null;
    }
    return null;
}

// Same as above but takes an arbitrary amount of parameters
private static ResultSet executeProcedure(String procedureName, String[] parameters, String connectionUrl, List<String> columns) {
    StringBuilder callStatement = new StringBuilder("{call " + procedureName + "(");
    for (int i = 0; i < parameters.length; i++) {
        callStatement.append("?");
        if (i < parameters.length - 1) {
            callStatement.append(", ");
        }
    }
    callStatement.append(")}");
    try (Connection connection = DriverManager.getConnection(connectionUrl);
         CallableStatement callableStatement = connection.prepareCall(callStatement.toString())) {
        for (int i = 0; i < parameters.length; i++) {
            callableStatement.setString(i + 1, parameters[i]);
        }
        boolean hasResultSet = callableStatement.execute();
        if (hasResultSet) {
            try (ResultSet resultSet = callableStatement.getResultSet()) {
                int count = 1;
                while (resultSet.next()) {
                    System.out.println("Result " + count + ":");
                    printLine();
                    for (String column : columns) {
                        try {
                            System.out.println(column + ": " + resultSet.getString(column));
                        } catch (SQLException e) {
                            System.out.println(column + ": N/A");
                        }
                    }
                    count++;
                    printLine();
                }
                return resultSet;
            }
        }
    } catch (SQLException e) {
        System.out.println(e.getMessage());
        return null;
    }
    return null;
}

// Method to run a stored procedure with an arbitrary amount of parameters and no result set
private static boolean executeProcedureNoResult(String procedureName, String[] parameters, String connectionUrl) {
    StringBuilder callStatement = new StringBuilder("{call " + procedureName + "(");
    for (int i = 0; i < parameters.length; i++) {
        callStatement.append("?");
        if (i < parameters.length - 1) {
            callStatement.append(", ");
        }
    }
    callStatement.append(")}");
    try (Connection connection = DriverManager.getConnection(connectionUrl);
         CallableStatement callableStatement = connection.prepareCall(callStatement.toString())) {
        for (int i = 0; i < parameters.length; i++) {
            callableStatement.setString(i + 1, parameters[i]);
        }
        callableStatement.execute();
        return true;
    } catch (SQLException e) {
        System.out.println(e.getMessage());
        return false;
    }
}

// Method to run simple queries that take one parameter
// This method is used for the more simple commands to avoid duplicate code
// Requires a list of columns to retrieve and print from the query
private static boolean executeQuery(String query, String parameter, String connectionUrl, List<String> columns) {
    try (Connection connection = DriverManager.getConnection(connectionUrl);
         PreparedStatement preparedStatement = connection.prepareStatement(query)) {
        // If parameter is integer, set as int, otherwise set as string
        try {
            preparedStatement.setInt(1, Integer.parseInt(parameter));
        } catch (NumberFormatException e) {
            preparedStatement.setString(1, "%" + parameter + "%");
        }
        ResultSet resultSet = preparedStatement.executeQuery();
        int count = 1;
        while (resultSet.next()) {
            System.out.println("Result " + count + ":");
            printLine();
            for (String column : columns) {
                // Check if column exists in result set
                try {
                    System.out.println(column + ": " + resultSet.getString(column));
                } catch (SQLException e) {
                    System.out.println(column + ": N/A");
                }
                if (column.equals("expiryDate")) { // Handle overdue checking at query time
                    try {
                        Date expiryDate = resultSet.getDate(column);
                        if (expiryDate.before(new Date(System.currentTimeMillis()))) {
                            System.out.println("(OVERDUE!)");
                        }
                    } catch (SQLException e) {
                        System.out.println(e.getMessage());
                    }
                }
            }
            count++;
            printLine();
        }
        resultSet.close();
        return true;
    } catch (SQLException e) {
        System.out.println(e.getMessage());
        return false;
    }
}

// Method to check out a book by its given ID (this finds a copy of the book that is available)
private static boolean checkoutBook(String[] tokens, String connectionUrl, int id) {
    String bookID = tokens[0];
    String memberID = Integer.toString(id); // Can pass in a member ID to check out a book for them
    // Call the stored procedure to check out a book (first parameter is member ID, second is book ID)
    String[] array = {memberID, bookID};
    if (executeProcedureNoResult("checkOutBook", array, connectionUrl)) {
        System.out.println("Book checked out successfully.");
        return true;
    } else {
        System.out.println("Error checking out book.");
        return false;
    }
}

// Method to check out a specific copy of a book, which is similar to checkoutBook but uses the copy ID directly
private static boolean checkoutCopy(String[] tokens, String connectionUrl, int id) {
    String copyID = tokens[0];
    String memberID = Integer.toString(id);
    // Call the stored procedure to check out a copy (first parameter is member ID, second is copy ID)
    String[] array = {memberID, copyID};
    if (executeProcedureNoResult("checkoutCopy", array, connectionUrl)) {
        System.out.println("Copy checked out successfully.");
        return true;
    } else {
        System.out.println("Error checking out copy.");
        return false;
    }
}

// This method places a hold on a book by its ID and is similar to checkoutBook but the MemberCopy created is 'held' instead of 'checkedOut'
private static boolean holdBook(String[] tokens, String connectionUrl, int id) {
    String bookID = tokens[0];
    String memberID = Integer.toString(id);
    // Call the stored procedure to hold a book (first parameter is member ID, second is book ID)
    String[] array = {memberID, bookID};
    if (executeProcedureNoResult("holdBook", array, connectionUrl)) {
        System.out.println("Book held successfully.");
        return true;
    } else {
        System.out.println("Error holding book.");
        return false;
    }
}

// This method places a hold on a specific copy of a book, which is similar to holdBook but uses the copy ID directly
private static boolean holdCopy(String[] tokens, String connectionUrl, int id) {
    String copyID = tokens[0];
    String memberID = Integer.toString(id);
    // Call the stored procedure to hold a copy (first parameter is member ID, second is copy ID)
    String[] array = {memberID, copyID};
    if (executeProcedureNoResult("holdCopy", array, connectionUrl)) {
        System.out.println("Copy held successfully.");
        return true;
    } else {
        System.out.println("Error holding copy.");
        return false;
    }
}

// Method to return a checked out copy of a book by its copyID, removing the row from MemberCopy
private static boolean returnCopy(String[] tokens, String connectionUrl, int id) {
    String copyID = tokens[0];
    // Call the stored procedure to return a copy by its ID
    String[] array = {copyID};
    if (executeProcedureNoResult("returnCopy", array, connectionUrl)) {
        System.out.println("Copy returned successfully.");
        return true;
    } else {
        System.out.println("Error returning copy.");
        return false;
    }
}

// Method to print all loans for a member
private static boolean printLoans(String[] tokens, String connectionUrl, int id) {
    String memberID = Integer.toString(id);
    // Call stored procedure to view all loans for a member
    return executeProcedure("getLoans", memberID, connectionUrl, MEMBER_COPY_COLUMNS) != null;
}

// Method to print all holds for a member
private static boolean printHolds(String[] tokens, String connectionUrl, int id) {
    String memberID = Integer.toString(id);
    // Call stored procedure to view all holds for a member
    return executeProcedure("getHolds", memberID, connectionUrl, MEMBER_COPY_COLUMNS) != null;
}

// Method to search for a book by its title
private static boolean searchTitle(String[] tokens, String connectionUrl) {
    String title = tokens[0];
    // Call stored procedure to search for a book by its title
    return executeProcedure("searchTitle", title, connectionUrl, BOOK_COLUMNS) != null;
}

// Method to search for a book by its author
public static boolean searchAuthor(String[] tokens, String connectionUrl) {
    String author = tokens[0];
    // Call stored procedure to search for a book by its author
    return executeProcedure("searchAuthor", author, connectionUrl, BOOK_COLUMNS) != null;
}

// Method to search for a book by its keywords (comma separated, AND's them)
private static boolean searchKeywords(String[] tokens, String connectionUrl) {
    // Special case--this was too complicated for us to find a way to do with a stored procedure
    String[] keywords = tokens[0].split(",");
    // This statement selects all rows from Book where there exists a Keyword row with the given word
    StringBuilder selectStatement = new StringBuilder("SELECT * FROM Book b WHERE ");
    for (int i = 0; i < keywords.length; i++) { // Add AND [exists keyword] for each keyword
        if (i > 0) {
            selectStatement.append(" AND ");
        }
        selectStatement.append("EXISTS (SELECT 1 FROM Keyword k WHERE k.bookID = b.bookID AND k.word = ?)");
    }

    try (Connection connection = DriverManager.getConnection(connectionUrl);
         PreparedStatement preparedStatement = connection.prepareStatement(selectStatement.toString())) {
        for (int i = 0; i < keywords.length; i++) {
            preparedStatement.setString(i + 1, keywords[i].trim());
        }
        // Print results as done in executeQuery
        ResultSet resultSet = preparedStatement.executeQuery();
        int count = 1;
        while (resultSet.next()) {
            System.out.println("Result " + count + ":");
            printLine();
            for (String column : BOOK_COLUMNS) {
                try {
                    System.out.println(column + ": " + resultSet.getString(column));
                } catch (SQLException e) {
                    System.out.println(column + ": N/A");
                }
            }
            count++;
            printLine();
        }
        resultSet.close();
        return true;
    } catch (SQLException e) {
        System.out.println(e.getMessage());
        return false;
    }
}

// Method to search for a book by its genre(s) (comma separated, AND's them)
public static boolean searchGenre(String[] tokens, String connectionUrl) {
    // Special case--this was too complicated for us to find a way to do with a stored procedure
    String[] genres = tokens[0].split(",");
    // This statement selects all rows from Book where there exists a BookGenre row with the given genre
    StringBuilder selectStatement = new StringBuilder("SELECT * FROM Book b WHERE ");
    for (int i = 0; i < genres.length; i++) { // Add AND [exists genre] for each genre
        if (i > 0) {
            selectStatement.append(" AND ");
        }
        selectStatement.append("EXISTS (SELECT 1 FROM BookGenre bg JOIN Genre g ON bg.genreID = g.genreID WHERE bg.bookID = b.bookID AND UPPER(g.genreName) = ?)");
    }
    try (Connection connection = DriverManager.getConnection(connectionUrl);
         PreparedStatement preparedStatement = connection.prepareStatement(selectStatement.toString())) {
        for (int i = 0; i < genres.length; i++) {
            preparedStatement.setString(i + 1, genres[i].trim().toUpperCase());
        }
        // Print results as done in executeQuery
        ResultSet resultSet = preparedStatement.executeQuery();
        int count = 1;
        while (resultSet.next()) {
            System.out.println("Result " + count + ":");
            printLine();
            for (String column : BOOK_COLUMNS) {
                try {
                    System.out.println(column + ": " + resultSet.getString(column));
                } catch (SQLException e) {
                    System.out.println(column + ": N/A");
                }
            }
            count++;
            printLine();
        }
        resultSet.close();
        return true;
    } catch (SQLException e) {
        System.out.println(e.getMessage());
        return false;
    }
}

// Method to search for a book by its ISBN
private static boolean searchISBN(String[] tokens, String connectionUrl) {
    String isbn = tokens[0];
    // Call stored procedure to search for a book by its ISBN
    return executeProcedure("searchISBN", isbn, connectionUrl, BOOK_COLUMNS) != null;
}

// Method to retrieve all details of a book by its ID
private static boolean bookDetails(String[] tokens, String connectionUrl) {
    String bookID = tokens[0];
    // Call stored procedure to find a book by its ID
    return executeProcedure("bookDetails", bookID, connectionUrl, BOOK_COLUMNS) != null;
}

// Method to find a member by their name
private static boolean findMemberByName(String[] tokens, String connectionUrl) {
    String name = tokens[0];
    // Call stored procedure to find a member by their name
    return executeProcedure("findMemberByName", name, connectionUrl, MEMBER_COLUMNS) != null;
}

// Method to find a member by their ID
private static boolean findMemberByID(String[] tokens, String connectionUrl) {
    String memberID = tokens[0];
    // Call stored procedure to find a member by their ID
    return executeProcedure("findMemberByID", memberID, connectionUrl, MEMBER_COLUMNS) != null;
}

// Method to add a member to the database with the given name, date of birth, and date of registration
private static boolean addMember(String[] tokens, String connectionUrl) {
    String firstName = tokens[0];
    String lastName = tokens[1];
    String dob = tokens[2];
    // Call stored procedure to add a member
    String[] array = {firstName, lastName, dob};
    if (executeProcedureNoResult("addMember", array, connectionUrl)) {
        // This statement selects a single member's ID by their first and last name
        String selectIDStatement = "SELECT TOP 1 memberID FROM Member WHERE memFirstName = ? AND memLastName = ?";
        try (Connection connection = DriverManager.getConnection(connectionUrl);
             PreparedStatement selectStmt = connection.prepareStatement(selectIDStatement)) {
            selectStmt.setString(1, firstName);
            selectStmt.setString(2, lastName);
            ResultSet resultSet = selectStmt.executeQuery();
            if (resultSet.next()) {
                System.out.println("Member " + firstName + " " + lastName + " added successfully with ID " + resultSet.getInt("memberID") + ".");
            } else {
                System.out.println("Error adding member.");
                return false;
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
        return true;
    } else {
        System.out.println("Error adding member.");
        return false;
    }

}

// Method to remove a member from the database by their ID
private static boolean removeMember(String[] tokens, String connectionUrl) {
    // Call stored procedure to remove a member by their ID
    return executeProcedureNoResult("removeMember", tokens, connectionUrl);
}

// Method to add a new author to the database
public static int createAuthorPrompt(String connectionUrl, String firstName, String lastName) {
    Scanner scanner = new Scanner(System.in);
    if (firstName != null && lastName != null) {
        System.out.println("Please fill out all available information for " + firstName + " " + lastName + ":");
    }
    // If first name and last name are not provided, prompt for them
    if (firstName == null) {
        System.out.println("Enter author's first name:");
        firstName = scanner.nextLine();
    }
    if (lastName == null) {
        System.out.println("Enter author's last name:");
        lastName = scanner.nextLine();
    }
    // Prompt for DOB and status
    System.out.println("Enter author's date of birth:");
    String dob = scanner.nextLine();
    System.out.println("Enter author's status (active, inactive, or unknown)");
    String status = scanner.nextLine();
    // Call stored procedure to add an author
    String[] array = {firstName, lastName, dob, status};
    if (executeProcedureNoResult("addAuthor", array, connectionUrl)) {
        // Continue to find the author's ID and return it
    } else {
        System.out.println("Error adding author.");
        return -1;
    }
    // This statement selects a single author by their first and last name
    String selectIDStatement = "SELECT TOP 1 authorID FROM Author WHERE firstName = ? AND lastName = ?";
    // Find and return author ID
    try (Connection connection = DriverManager.getConnection(connectionUrl);
         PreparedStatement selectStmt = connection.prepareStatement(selectIDStatement)) {
        selectStmt.setString(1, firstName);
        selectStmt.setString(2, lastName);
        ResultSet resultSet = selectStmt.executeQuery();
        if (resultSet.next()) {
            System.out.println("Author " + firstName + " " + lastName + " added successfully with ID " + resultSet.getInt("authorID") + ".");
            return resultSet.getInt("authorID");
        } else {
            System.out.println("Error adding author.");
            return -1;
        }
    } catch (SQLException e) {
        System.out.println(e.getMessage());
        return -1;
    }
}

// Method to add a new genre to the database
private static int createGenrePrompt(String connectionUrl, String genre) {
    Scanner scanner = new Scanner(System.in);
    if (genre != null) {
        System.out.println("Please fill out all available information for " + genre + ":");
    } else {
        System.out.println("Enter genre name:");
        genre = scanner.nextLine();
    }
    System.out.println("Enter genre description:");
    String description = scanner.nextLine();
    // Call stored procedure to add a genre
    String[] array = {genre, description};
    if (executeProcedureNoResult("addGenre", array, connectionUrl)) {
        // Continue to find the genre's ID and return it
    } else {
        System.out.println("Error adding genre.");
        return -1;
    }
    // This statement selects a single genre by its name
    String selectIDStatement = "SELECT TOP 1 genreID FROM Genre WHERE genreName = ?";
    // Find and return genre ID
    try (Connection connection = DriverManager.getConnection(connectionUrl);
         PreparedStatement selectStmt = connection.prepareStatement(selectIDStatement)) {
        selectStmt.setString(1, genre);
        ResultSet resultSet = selectStmt.executeQuery();
        if (resultSet.next()) {
            System.out.println("Genre " + genre + " added successfully with ID " + resultSet.getInt("genreID") + ".");
            return resultSet.getInt("genreID");
        } else {
            System.out.println("Error adding genre.");
            return -1;
        }
    } catch (SQLException e) {
        System.out.println(e.getMessage());
        return -1;
    }
}

// This method attempts to add a genre (by name, argument 3) to a book (by ID, argument 2)
private static boolean addGenreToBook(String[] tokens, String connectionUrl) {
    String bookID = tokens[0];
    String genreName = tokens[1];
    // Determine if the genre exists
    try (Connection connection = DriverManager.getConnection(connectionUrl);
         PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM Genre WHERE genreName = ?")) {
        preparedStatement.setString(1, genreName);
        ResultSet resultSet = preparedStatement.executeQuery();
        if (!resultSet.next()) { // If the genre does not exist, prompt to create it
            System.out.println("Genre not found, opening new genre prompt!");
            // Call createGenrePrompt to add new genre
            int genreID = createGenrePrompt(connectionUrl, genreName);
            if (genreID == -1) {
                return false;
            }
        }
    } catch (SQLException e) {
        System.out.println(e.getMessage());
        return false;
    }
    // Call stored procedure to add the genre with this name to the book with this ID
    String[] array = {bookID, genreName};
    if (executeProcedureNoResult("addBookGenre", array, connectionUrl)) {
        System.out.println("Genre added to book successfully.");
        return true;
    } else {
        System.out.println("Error adding genre to book.");
        return false;
    }
}

// Method to remove a genre (by name, argument 3) from a book (by ID, argument 2)
private static boolean removeGenreFromBook(String[] tokens, String connectionUrl) {
    String bookID = tokens[0];
    String genreName = tokens[1];
    // Call stored procedure to remove the genre with this name from the book with this ID
    String[] array = {bookID, genreName};
    if (executeProcedureNoResult("removeBookGenre", array, connectionUrl)) {
        System.out.println("Genre removed from book successfully.");
        return true;
    } else {
        System.out.println("Error removing genre from book.");
        return false;
    }
}

// Method to add a keyword (argument 3) to a book (by ID, argument 2)
private static boolean addKeywordToBook(String[] tokens, String connectionUrl) {
    String bookID = tokens[0];
    String keyword = tokens[1];
    // Call stored procedure to add the keyword with this name to the book with this ID
    String[] array = {bookID, keyword};
    if (executeProcedureNoResult("addKeyword", array, connectionUrl)) {
        System.out.println("Keyword added to book successfully.");
        return true;
    } else {
        System.out.println("Error adding keyword to book.");
        return false;
    }
}

// Method to remove a keyword (argument 3) from a book (by ID, argument 2)
private static boolean removeKeywordFromBook(String[] tokens, String connectionUrl) {
    String bookID = tokens[0];
    String keyword = tokens[1];
    // Call stored procedure to remove the keyword with this name from the book with this ID
    String[] array = {bookID, keyword};
    if (executeProcedureNoResult("removeKeyword", array, connectionUrl)) {
        System.out.println("Keyword removed from book successfully.");
        return true;
    } else {
        System.out.println("Error removing keyword from book.");
        return false;
    }
}

/// Method to update an author's information by their ID
private static boolean updateAuthor(String[] tokens, String connectionUrl) {
    // Similar to createAuthorPrompt, but takes an author ID as an argument
    int authorID = Integer.parseInt(tokens[0]);
    Scanner scanner = new Scanner(System.in);
    System.out.println("Enter author's first name:");
    String firstName = scanner.nextLine();
    System.out.println("Enter author's last name:");
    String lastName = scanner.nextLine();
    System.out.println("Enter author's date of birth:");
    String dob = scanner.nextLine();
    System.out.println("Enter author's status (active, inactive, or unknown):");
    String status = scanner.nextLine();
    // Call stored procedure to update an author by their ID
    String[] array = {Integer.toString(authorID), firstName, lastName, dob, status};
    if (executeProcedureNoResult("updateAuthor", array, connectionUrl)) {
        System.out.println("Author updated successfully.");
        return true;
    } else {
        System.out.println("Error updating author.");
        return false;
    }
}

// Method to prompt the user to add a book to the database
private static boolean addBook(String connectionUrl) {
    // Prompt user for book details
    Scanner scanner = new Scanner(System.in);
    System.out.println("Enter book title:");
    String title = scanner.nextLine();
    System.out.println("ISBN, enter for N/A:");
    String isbn = scanner.nextLine();
    System.out.println("Edition, enter for N/A:");
    String edition = scanner.nextLine();
    System.out.println("Publication date, enter for N/A:");
    String publicationDate = scanner.nextLine();
    System.out.println("Publisher, enter for N/A:");
    String publisher = scanner.nextLine();
    System.out.println("Copyright year, enter for N/A:");
    String copyYear = scanner.nextLine();
    System.out.println("Authors (firstName lastName), primary author first, comma separated:");
    String authors = scanner.nextLine();
    // Call stored procedure to add a book
    String[] array = {title, isbn, edition, publicationDate, publisher, copyYear};
    if (executeProcedureNoResult("addBook", array, connectionUrl)) {
        // Continue to add authors to the book
    } else {
        System.out.println("Error adding book.");
        return false;
    }
    // This statement selects the author ID of the newly inserted author(s)
    String selectAuthorStatement = "SELECT authorID FROM Author WHERE lastName = ? AND (firstName = ? OR firstName IS NULL)";
    // This statement inserts a new row into BookAuthor with the given book ID and author ID
    String insertBookAuthorStatement = "INSERT INTO BookAuthor (bookID, authorID, isPrimaryAuthor) VALUES (?, ?, ?)";
    // This statement selects the ID of the added book
    String selectBookIDStatement = "SELECT TOP 1 bookID FROM Book WHERE title = ? AND ISBN = ? AND edition = ? AND publicationDate = ? AND publisher = ? AND copyrightYear = ?";
    try (Connection connection = DriverManager.getConnection(connectionUrl);
         PreparedStatement selectAuthorStmt = connection.prepareStatement(selectAuthorStatement);
            PreparedStatement selectBookIDStmt = connection.prepareStatement(selectBookIDStatement);
         PreparedStatement insertBookAuthorStmt = connection.prepareStatement(insertBookAuthorStatement)) {
        // Populate query arguments
        selectBookIDStmt.setString(1, title);
        selectBookIDStmt.setString(2, isbn);
        selectBookIDStmt.setString(3, edition);
        selectBookIDStmt.setString(4, publicationDate);
        selectBookIDStmt.setString(5, publisher);
        selectBookIDStmt.setString(6, copyYear);
        ResultSet generatedKeys = selectBookIDStmt.executeQuery();
        // Get generated bookID
        if (generatedKeys.next()) {
            int bookID = generatedKeys.getInt(1);
            // Process authors
            String[] authorNames = authors.split(",");
            for (int i = 0; i < authorNames.length; i++) { // For each author...
                // Split name into first and last
                String[] nameParts = authorNames[i].trim().split(" ");
                String lastName = nameParts[0];
                String firstName = nameParts.length > 1 ? nameParts[1] : null;
                // Check if this author already exists
                selectAuthorStmt.setString(1, firstName);
                selectAuthorStmt.setString(2, lastName);
                ResultSet authorResult = selectAuthorStmt.executeQuery();
                int authorID;
                if (authorResult.next()) { // If the author is found, use their ID
                    authorID = authorResult.getInt("authorID");
                } else { // If the author is not found, prompt to create a new author
                    System.out.println("Author not found, opening new author prompt!");
                    // Call createAuthorPrompt to add new author
                    authorID = createAuthorPrompt(connectionUrl, firstName, lastName);
                }
                // Insert into BookAuthor
                insertBookAuthorStmt.setInt(1, bookID);
                insertBookAuthorStmt.setInt(2, authorID);
                insertBookAuthorStmt.setBoolean(3, i == 0); // First author is primary, others are not
                insertBookAuthorStmt.executeUpdate();
                return true;
            }
        } else {
            System.out.println("Error adding book.");
            return false;
        }
    } catch (SQLException e) {
        System.out.println(e.getMessage());
        return false;
    }
    return false;
}

// Method to find an author by their name or ID
private static boolean findAuthor(String[] tokens, String connectionUrl, String type) {
    String lastName = tokens[0];
    String selectStatement = "";
    // Select statement depends on whether the search is by name or ID
    if (type.equals("name")) {
        // Call stored procedure to find author by name
        return executeProcedure("findAuthorName", lastName, connectionUrl, AUTHOR_COLUMNS) != null;
    } else if (type.equals("id")) {
        // Call stored procedure to find author by ID
        return executeProcedure("findAuthorID", lastName, connectionUrl, AUTHOR_COLUMNS) != null;
    }
    return false;
}

// Method to add a copy when given a book ID and condition
public static boolean addCopy(String[] tokens, String connectionUrl) {
    String bookID = tokens[0];
    String condition = tokens[1];
    if (!condition.equals("good") && !condition.equals("neutral") && !condition.equals("poor")) {
        System.out.println("Invalid condition. Please enter 'good', 'neutral', or 'poor'.");
        return false;
    }
    // Call stored procedure to add a copy of the book with this ID and condition
    String[] array = {bookID, condition};
    if (executeProcedureNoResult("addCopy", array, connectionUrl)) {
        System.out.println("Copy added successfully.");
        return true;
    } else {
        System.out.println("Error adding copy.");
        return false;
    }
}

// Method to remove a copy by its ID
public static boolean removeCopy(String[] tokens, String connectionUrl) {
    String copyID = tokens[0];
    // Call stored procedure to remove a copy by its ID
    String[] array = {copyID};
    if (executeProcedureNoResult("removeCopy", array, connectionUrl)) {
        System.out.println("Copy removed successfully.");
        return true;
    } else {
        System.out.println("Error removing copy.");
        return false;
    }
}

// Method to return a list of user-defined parameters
public static String[] promptInput(String arguments) {
    if (arguments == null || arguments.isEmpty()) {
        return new String[0];
    }
    System.out.println("Enter the following arguments separated by spaces:");
    System.out.println(arguments);
    Scanner scanner = new Scanner(System.in);
    String input = scanner.nextLine();
    return input.split(" ");
}

private static void printAvailableCommands() {
    System.out.println("Available commands:");
    System.out.println("1. View details about a book <bookID>");
    System.out.println("2. Search for books by <title>");
    System.out.println("3. Search for books by author <author>");
    System.out.println("4. Search for books with all keywords <keyword1, keyword2, ...>");
    System.out.println("5. Search for books by ISBN <isbn>");
    System.out.println("6. Search for books with all genres <genre1, genre2, ...>");

    if (userType == USER_TYPE.MEMBER) {
        System.out.println("7. Hold a book <bookID>");
        System.out.println("8. Hold a copy <copyID>");
        System.out.println("9. View your loans");
        System.out.println("10. View your holds");
        System.out.println("11. Return a copy you've checked out <copyID>");
        System.out.println("12. Check out an available copy of a book <bookID>");
        System.out.println("13. Check out a specific copy <copyID>");
    }
    if (userType == USER_TYPE.EMPLOYEE || userType == USER_TYPE.CURATOR) {
        System.out.println("7. Hold a book for a member <bookID> <memberID>");
        System.out.println("8. Hold a copy for a member <copyID> <memberID>");
        System.out.println("9. Check out a book for a member <bookID> <memberID>");
        System.out.println("10. Check out a copy for a member <copyID> <memberID>");
        System.out.println("11. Return a copy for a member <copyID> <memberID>");
        System.out.println("12. Find a member by name <name>");
        System.out.println("13. Find a member by ID <memberID>");
        System.out.println("14. Add a new member <firstName> <lastName> <date of birth>");
        System.out.println("15. Remove a member <memberID>");
        System.out.println("16. View a member's loans <memberID>");
        System.out.println("17. View a member's holds <memberID>");
    }
    if (userType == USER_TYPE.CURATOR) {
        System.out.println("18. Add a new book");
        System.out.println("19. Add a new author");
        System.out.println("20. Find an author by name <name>");
        System.out.println("21. Find an author by ID <authorID>");
        System.out.println("22. Update an author by ID <authorID>");
        System.out.println("23. Add a new genre");
        System.out.println("24. Add a genre to a book <bookID> <genreName>");
        System.out.println("25. Remove a genre from a book <bookID> <genreName>");
        System.out.println("26. Add a keyword to a book <bookID> <keyword>");
        System.out.println("27. Remove a keyword from a book <bookID> <keyword>");
        System.out.println("28. Add a copy of a book <bookID> <condition>");
        System.out.println("29. Remove a copy of a book <copyID>");
    }
    System.out.println("(Type 'exit' to quit.)");
}

// Method to handle all command parsing logic
private static boolean parseCommand(String command, String connectionUrl) {
    printLine();
    int number = -1;
    try {
        number = Integer.parseInt(command);
    } catch (NumberFormatException e) {
        System.out.println("Invalid command. Please enter a number.");
        return false;
    }
    switch (number) {
        case 1:
            String[] bookDetailsArgs = promptInput("bookID");
            return bookDetails(bookDetailsArgs, connectionUrl);
        case 2:
            String[] searchTitleArgs = promptInput("title");
            return searchTitle(searchTitleArgs, connectionUrl);
        case 3:
            String[] searchAuthorArgs = promptInput("author");
            return searchAuthor(searchAuthorArgs, connectionUrl);
        case 4:
            String[] searchKeywordsArgs = promptInput("keywords (comma separated, not space separated)");
            return searchKeywords(searchKeywordsArgs, connectionUrl);
        case 5:
            String[] searchISBNArgs = promptInput("isbn");
            return searchISBN(searchISBNArgs, connectionUrl);
        case 6:
            String[] searchGenreArgs = promptInput("genres (comma separated, not space separated)");
            return searchGenre(searchGenreArgs, connectionUrl);
        case 7:
            if (userType == USER_TYPE.MEMBER) {
                String[] holdBookArgs = promptInput("bookID");
                return holdBook(holdBookArgs, connectionUrl, memberID);
            } else if (userType == USER_TYPE.EMPLOYEE || userType == USER_TYPE.CURATOR) {
                String[] holdForBookArgs = promptInput("bookID memberID");
                return holdBook(holdForBookArgs, connectionUrl, Integer.parseInt(holdForBookArgs[1]));
            }
            break;
        case 8:
            if (userType == USER_TYPE.MEMBER) {
                String[] holdCopyArgs = promptInput("copyID");
                return holdCopy(holdCopyArgs, connectionUrl, memberID);
            } else if (userType == USER_TYPE.EMPLOYEE || userType == USER_TYPE.CURATOR) {
                String[] holdForCopyArgs = promptInput("copyID memberID");
                return holdCopy(holdForCopyArgs, connectionUrl, Integer.parseInt(holdForCopyArgs[1]));
            }
            break;
        case 9:
            if (userType == USER_TYPE.MEMBER) {
                String[] loansArgs = promptInput("");
                return printLoans(loansArgs, connectionUrl, memberID);
            } else if (userType == USER_TYPE.EMPLOYEE || userType == USER_TYPE.CURATOR) {
                String[] checkOutForBookArgs = promptInput("bookID memberID");
                return checkoutBook(checkOutForBookArgs, connectionUrl, Integer.parseInt(checkOutForBookArgs[1]));
            }
            break;
        case 10:
            if (userType == USER_TYPE.MEMBER) {
                String[] holdsArgs = promptInput("");
                return printHolds(holdsArgs, connectionUrl, memberID);
            } else if (userType == USER_TYPE.EMPLOYEE || userType == USER_TYPE.CURATOR) {
                String[] checkOutForCopyArgs = promptInput("copyID memberID");
                return checkoutCopy(checkOutForCopyArgs, connectionUrl, Integer.parseInt(checkOutForCopyArgs[1]));
            }
            break;
        case 11:
            if (userType == USER_TYPE.MEMBER) {
                String[] returnCopyArgs = promptInput("copyID");
                return returnCopy(returnCopyArgs, connectionUrl, memberID);
            } else if (userType == USER_TYPE.EMPLOYEE || userType == USER_TYPE.CURATOR) {
                String[] returnForCopyArgs = promptInput("copyID memberID");
                return returnCopy(returnForCopyArgs, connectionUrl, Integer.parseInt(returnForCopyArgs[1]));
            }
            break;
        case 12:
            if (userType == USER_TYPE.MEMBER) {
                String[] checkOutBookArgs = promptInput("bookID");
                return checkoutBook(checkOutBookArgs, connectionUrl, memberID);
            } else if (userType == USER_TYPE.EMPLOYEE || userType == USER_TYPE.CURATOR) {
                String[] findMemberArgs = promptInput("name");
                return findMemberByName(findMemberArgs, connectionUrl);
            }
            break;
        case 13:
            if (userType == USER_TYPE.MEMBER) {
                String[] checkOutCopyArgs = promptInput("copyID");
                return checkoutCopy(checkOutCopyArgs, connectionUrl, memberID);
            } else if (userType == USER_TYPE.EMPLOYEE || userType == USER_TYPE.CURATOR) {
                String[] findMemberArgs = promptInput("ID");
                return findMemberByID(findMemberArgs, connectionUrl);
            }
            break;
        case 14:
            if (userType == USER_TYPE.EMPLOYEE || userType == USER_TYPE.CURATOR) {
                String[] addMemberArgs = promptInput("firstName lastName dob");
                return addMember(addMemberArgs, connectionUrl);
            }
            break;
        case 15:
            if (userType == USER_TYPE.EMPLOYEE || userType == USER_TYPE.CURATOR) {
                String[] removeMemberArgs = promptInput("memberID");
                return removeMember(removeMemberArgs, connectionUrl);
            }
            break;
        case 16:
            if (userType == USER_TYPE.EMPLOYEE || userType == USER_TYPE.CURATOR) {
                String[] loansArgs = promptInput("memberID");
                return printLoans(loansArgs, connectionUrl, Integer.parseInt(loansArgs[0]));
            }
            break;
        case 17:
            if (userType == USER_TYPE.EMPLOYEE || userType == USER_TYPE.CURATOR) {
                String[] holdsArgs = promptInput("memberID");
                return printHolds(holdsArgs, connectionUrl, Integer.parseInt(holdsArgs[0]));
            }
            break;
        case 18:
            if (userType == USER_TYPE.CURATOR) {
                return addBook(connectionUrl);
            }
            break;
        case 19:
            if (userType == USER_TYPE.CURATOR) {
                createAuthorPrompt(connectionUrl, null,  null);
                return true;
            }
            break;
        case 20:
            if (userType == USER_TYPE.CURATOR) {
                String[] findAuthorArgs = promptInput("name");
                return findAuthor(findAuthorArgs, connectionUrl, "name");
            }
            break;
        case 21:
            if (userType == USER_TYPE.CURATOR) {
                String[] findAuthorArgs = promptInput("ID");
                return findAuthor(findAuthorArgs, connectionUrl, "id");
            }
            break;
        case 22:
            if (userType == USER_TYPE.CURATOR) {
                String[] updateAuthorArgs = promptInput("authorID");
                return updateAuthor(updateAuthorArgs, connectionUrl);
            }
            break;
        case 23:
            if (userType == USER_TYPE.CURATOR) {
                String[] addGenreArgs = promptInput("genreName");
                createGenrePrompt(connectionUrl, addGenreArgs[0]);
                return true;
            }
            break;
        case 24:
            if (userType == USER_TYPE.CURATOR) {
                String[] addGenreToBookArgs = promptInput("bookID genreName");
                return addGenreToBook(addGenreToBookArgs, connectionUrl);
            }
            break;
        case 25:
            if (userType == USER_TYPE.CURATOR) {
                String[] removeGenreFromBookArgs = promptInput("bookID genreName");
                return removeGenreFromBook(removeGenreFromBookArgs, connectionUrl);
            }
            break;
        case 26:
            if (userType == USER_TYPE.CURATOR) {
                String[] addKeywordToBookArgs = promptInput("bookID keyword");
                return addKeywordToBook(addKeywordToBookArgs, connectionUrl);
            }
            break;
        case 27:
            if (userType == USER_TYPE.CURATOR) {
                String[] removeKeywordFromBookArgs = promptInput("bookID keyword");
                return removeKeywordFromBook(removeKeywordFromBookArgs, connectionUrl);
            }
            break;
        case 28:
            if (userType == USER_TYPE.CURATOR) {
                String[] addCopyArgs = promptInput("bookID condition");
                return addCopy(addCopyArgs, connectionUrl);
            }
            break;
        case 29:
            if (userType == USER_TYPE.CURATOR) {
                String[] removeCopyArgs = promptInput("copyID");
                return removeCopy(removeCopyArgs, connectionUrl);
            }
            break;
        default:
            System.out.println("Invalid command.");
            break;
    }
    return false;
}



}
