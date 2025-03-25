-- PERMISSIONS
Create role selectrole;
Grant select on book to selectrole;
Grant select on author to selectrole;
Grant select on bookAuthor to selectrole;
Grant select on keyword to selectrole;
Grant select on reference to selectrole;
Grant select on genre to selectrole;
Grant select on bookGenre to selectrole;
Grant select on member to selectrole;
Grant select on copy to selectrole;
Grant select on memberCopy to selectrole;
Alter role selectrole add member member_user;
Alter role selectrole add member employee_user;
Alter role selectrole add member curator_user;

Grant execute to member_user;
Grant execute to employee_user;
Grant execute to curator_user;

Create role checkoutrole;
Grant insert, update, delete on memberCopy to checkoutrole;
Alter role checkoutrole add member member_user;
Alter role checkoutrole add member employee_user;
Alter role checkoutrole add member curator_user;

Grant insert, update, delete on book to curator;
Grant insert, update, delete on author to curator;
Grant insert, update, delete on bookAuthor to curator;
Grant insert, update, delete on keyword to curator;
Grant insert, update, delete on reference to curator;
Grant insert, update, delete on genre to curator;
Grant insert, update, delete on bookGenre to curator;
Grant insert, update, delete on copy to curator;

Create role membermanagementrole;
Grant insert, update, delete on member to membermanagementrole;
Alter role membermanagementrole add member employee_user;
Alter role membermanagementrole add member curator_user;


--A lot of our stored procedures used DML, we have included them below
CREATE OR ALTER  PROCEDURE checkOutBook @memID int, @bookID int  
AS  
BEGIN   
	SET NOCOUNT ON   
	INSERT INTO MemberCopy (memberID, copyID, memCopyStatus, createdDate, expiryDate)    
	VALUES (@memID, 	(SELECT TOP 1 copyID FROM Copy 	WHERE bookID = @bookID AND copyID NOT IN 	 
	(SELECT copyID FROM MemberCopy  	WHERE memCopyStatus = 'checkedOut' OR memCopyStatus = 'held')),
	'checkedOut', GETDATE(), DATEADD(day, 14, GETDATE()))  END  
GO

CREATE OR ALTER   PROCEDURE checkoutCopy @memID int, @copyID int  
AS  
BEGIN
	SET NOCOUNT ON   
	DELETE FROM MemberCopy WHERE memberID = @memID AND copyID = @copyID;  
END
GO

CREATE OR ALTER PROCEDURE holdBook @memID int, @bookID int  
AS
BEGIN
	SET NOCOUNT ON
	INSERT INTO MemberCopy (memberID, copyID, memCopyStatus, createdDate, expiryDate)    
	VALUES (@memID, 	(SELECT TOP 1 copyID FROM Copy 	WHERE bookID = @bookID AND copyID NOT IN 	 
	(SELECT copyID FROM MemberCopy  	WHERE memCopyStatus = 'checkedOut' OR memCopyStatus = 'held'))   
	, 'held', GETDATE(), DATEADD(day, 14, GETDATE()))  
END
GO

CREATE OR ALTER PROCEDURE holdCopy @memID int, @copyID int  
AS  
BEGIN   
	SET NOCOUNT ON
	INSERT INTO MemberCopy (memberID, copyID, memCopyStatus, createdDate, expiryDate)
	VALUES (@memID, @copyID, 'held', GETDATE(), DATEADD(day, 14, GETDATE()))
END
GO

CREATE OR ALTER PROCEDURE getLoans @memID int  
AS  
BEGIN   
	SET NOCOUNT ON
	SELECT * FROM MemberCopy WHERE memberID = @memID AND memCopyStatus = 'checkedOut'  
END
GO

CREATE OR ALTER PROCEDURE getHolds @memID int  
AS  
BEGIN   
	SET NOCOUNT ON   
	SELECT * FROM MemberCopy WHERE memberID = @memID AND memCopyStatus = 'held'  
END
GO

CREATE OR ALTER PROCEDURE searchTitle @title varchar(255)  
AS
BEGIN
	SET NOCOUNT ON
	SELECT * FROM Book WHERE title LIKE '%' + @title + '%'  
END
GO


CREATE OR ALTER PROCEDURE searchAuthor @author varchar(255)  
AS  
BEGIN
	SET NOCOUNT ON   
	SELECT * FROM Book b   JOIN BookAuthor ba ON b.bookID = ba.bookID   JOIN AUTHOR a ON ba.authorID = a.authorID   
	WHERE a.lastName LIKE '%'+@author+'%' OR a.firstName LIKE '%'+@author+'%'
END
GO

CREATE OR ALTER PROCEDURE searchISBN @isbn varchar(255)
AS
BEGIN
	SET NOCOUNT ON   
	SELECT * FROM Book WHERE ISBN = @isbn  
END  
GO

CREATE OR ALTER PROCEDURE findMemberByName @name varchar(255)  
AS
BEGIN
	SET NOCOUNT ON   
	SELECT * FROM member   
	WHERE memFirstName like '%'+@name+'%' OR memLastName like '%'+@name+'%'  
END
GO

CREATE OR ALTER PROCEDURE findMemberByID @memID varchar(255)  
AS
BEGIN
	SET NOCOUNT ON   
	SELECT * FROM member   
	WHERE memberID = @memID;  
END
GO

CREATE OR ALTER PROCEDURE removeMember @memID int  
AS  
BEGIN   
	SET NOCOUNT ON   
	DELETE FROM memberCopy where memberID = @memID   
	DELETE FROM member where memberID = @memID  
END
GO

CREATE OR ALTER PROCEDURE addAuthor	@firstName varchar(255),   @lastName varchar(255),   @dob date,   @status varchar(8)   
AS  
BEGIN
	SET NOCOUNT ON
	INSERT INTO author VALUES (NULLIF(@firstName, ''), NULLIF(@lastName, ''), NULLIF(@dob, ''), @status)    
END  
GO

CREATE OR ALTER PROCEDURE addGenre	@genreName varchar(255),   @genreDesc varchar(255)  
AS  
BEGIN
	SET NOCOUNT ON
	INSERT INTO genre VALUES (@genreName, @genreDesc)  
END 

CREATE OR ALTER PROCEDURE updateAuthor
	@authorID int, @firstName varchar(255), @lastName varchar(255), @dob date, @status varchar(8)
AS
BEGIN
	SET NOCOUNT ON
	IF @firstName = '' OR @firstName = NULL
	BEGIN
    	SET @firstName = (SELECT firstName FROM Author WHERE authorID = @authorID);
	END
	IF @lastName = '' OR @lastName = NULL
	BEGIN
    	SET @lastName = (SELECT lastName FROM Author WHERE authorID = @authorID);
	END
	IF @dob = '' OR @firstName = NULL
	BEGIN
    	SET @dob = (SELECT dob FROM Author WHERE authorID = @authorID);
	END
	IF @status = '' OR @status = NULL
	BEGIN
    	SET @status = (SELECT status FROM Author WHERE authorID = @authorID);
	END
	UPDATE author SET firstName = @firstName, lastName = @lastName, dob = @dob, status = @status WHERE authorID = @authorID;
END
GO

CREATE OR ALTER PROCEDURE returnCopy @copyID int
AS
BEGIN
	SET NOCOUNT ON
	DELETE FROM memberCopy WHERE copyID = @copyID
END
GO

CREATE OR ALTER PROCEDURE addBook
	@title varchar(255), @isbn char(13), @edition int, @pubDate date, @pubName varchar(255), @copyYear int
AS
BEGIN
	SET NOCOUNT ON
	INSERT INTO book VALUES(@title, NULLIF(@isbn, ''), @edition, NULLIF(@pubDate, ''), NULLIF(@pubName, ''), @copyYear)
END
GO

CREATE OR ALTER PROCEDURE addBookGenre @bookID int, @genreName varchar(255)
AS
BEGIN
	SET NOCOUNT ON
	INSERT INTO bookGenre VALUES (@bookID, (SELECT genreID from genre where genreName = @genreName))
END
GO

CREATE OR ALTER PROCEDURE removeBookGenre @bookID int, @genreID int
AS
BEGIN
	SET NOCOUNT ON
	DELETE FROM bookGenre WHERE bookID = @bookID AND genreID = @genreID
END
GO


CREATE OR ALTER PROCEDURE addKeyword @bookID int, @keyWord varchar(255)
AS
BEGIN
	SET NOCOUNT ON
	INSERT INTO keyword VALUES (@bookID, @keyWord)
END
GO

CREATE OR ALTER PROCEDURE removeKeyword @bookID int, @keyWord varchar(255)
AS
BEGIN
	SET NOCOUNT ON
	DELETE FROM keyword WHERE bookID = @bookID AND word = @keyWOrd
END
GO


CREATE OR ALTER PROCEDURE addCopy @bookID int, @condition varchar(7)
AS
BEGIN
	SET NOCOUNT ON
	INSERT INTO copy VALUES (@bookID, @condition)
END
GO

CREATE OR ALTER PROCEDURE removeCopy @copyID int
AS
BEGIN
	SET NOCOUNT ON
	DELETE FROM copy WHERE copyID = @copyID
END
GO

CREATE OR ALTER PROCEDURE bookDetails @bookID int
AS
BEGIN
	SET NOCOUNT ON
	SELECT * FROM book WHERE bookID = @bookID
END
GO

CREATE OR ALTER PROCEDURE addMember @firstName varchar(255), @lastName varchar(255), @dob date
AS
BEGIN
	SET NOCOUNT ON
	INSERT INTO member VALUES (@firstName, @lastName, @dob, GETDATE())
END
GO


CREATE OR ALTER PROCEDURE findAuthorName @authName varchar(255)
AS
BEGIN
	SET NOCOUNT ON
	SELECT * FROM author WHERE firstName like '%'+@authName+'%' OR lastName like '%'+@authName+'%'
END
GO


CREATE OR ALTER PROCEDURE findAuthorID @authorID int
AS
BEGIN
	SET NOCOUNT ON
	SELECT * FROM author WHERE authorID = @authorID
END
GO
