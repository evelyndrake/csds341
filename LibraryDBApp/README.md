## Getting Started

Welcome to the VS Code Java world. Here is a guideline to help you get started to write Java code in Visual Studio Code.

## Folder Structure

The workspace contains two folders by default, where:

- `src`: the folder to maintain sources
- `lib`: the folder to maintain dependencies

Meanwhile, the compiled output files will be generated in the `bin` folder by default.

> If you want to customize the folder structure, open `.vscode/settings.json` and update the related settings there.

## Dependency Management

The `JAVA PROJECTS` view allows you to manage your dependencies. More details can be found [here](https://github.com/microsoft/vscode-java-dependency#manage-dependencies).

public static void searchKeywords(String[] tokens, String connectionUrl) {
        int numKw = tokens[2].split(",").length;
        String kwSet = "'" + tokens[2].replaceAll(",", "', '") + ("'");
        String selectStatement = "Select * from book b "
                +"outer apply( "
                +"select count(*) as kwCount "
                +"from keyword k where b.bookID = k.bookID and k.word in (?)"
                +") as bookwords where kwCount = " + numKw;
        executeQuery(selectStatement, kwSet, connectionUrl, BOOK_COLUMNS);
}

