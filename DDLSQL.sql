--creating a table for book
CREATE TABLE book(
	--auto incrementing primary key for bookID
	bookID int identity(1,1) primary key not null,
	title varchar(255) not null,
	--enforced on front end to only allow numeric input
	ISBN varchar(13) null,
	edition varchar(255) null,
	publicationDate date null,
	publisher varchar(255) null,
	copyrightYear int null
);
--realized issue with book
ALTER TABLE book
ALTER COLUMN ISBN char(13);


--creating a table for author
CREATE TABLE author(
	authorID int identity(1,1) primary key not null,
	firstName varchar(255) null,
	lastName varchar(255) null,
	dob date null,
	status varchar(8) check (status in('active', 'inactive', 'unknown')) not null
);
ALTER TABLE author ADD CONSTRAINT UC_Authors UNIQUE(firstName, lastName, dob);

--BookAuthor

--creating table for book author
CREATE TABLE bookAuthor(
	bookID int,
	authorID int,
	--0 for false, 1 for true
	isPrimaryAuthor bit not null,
	foreign key (bookID) references book(bookID),
	foreign key (authorID) references author(authorID),
	primary key(bookID, authorID)
);

--Creating table for keyword
CREATE TABLE keyword(
	bookID int,
	word varchar(255) not null,
	foreign key (bookID) references book(bookID),
	primary key (bookID, word)
);

--Creating table for reference
CREATE TABLE reference(
	referencingBookID int,
	referencedBookID int,
	foreign key (referencingBookID) references book(bookID),
	foreign key (referencedBookID) references book(bookID),
	primary key(referencingBookID, referencedBookID),
	check(referencingBookID != referencedBookID)
);

--creating table for genre
CREATE TABLE genre(
	genreID int identity(1,1) primary key not null,
	genreName varchar(255) not null,
	genreDescription varchar(255) null

);
ALTER TABLE genre ADD CONSTRAINT UC_Genre UNIQUE(genreName);

--Creating table for bookGenre
CREATE TABLE bookGenre(
	bookID int,
	genreID int,
	foreign key (bookID) references book(bookID),
	foreign key (genreID) references genre(genreID),
	primary key(bookID, genreID)
);

--Creating table for member
CREATE TABLE member(
	--auto incrementing primary key for memberID
	memberID int identity(1,1) primary key not null,
	memFirstName varChar(255) not null,
	memLastName varChar(255) not null,
	--date of birth
	memdob date not null,
	--date of registration
	memdor date not null

);

-- Creating copy
CREATE TABLE copy(
	--auto incrementing primary key for copyID
	copyID int identity(1,1) primary key not null,
	bookID int,
	foreign key (bookID) references book(bookID),
	condition varchar(7) check(condition in('good','neutral','poor')) not null

);

--Creating memberCopy
CREATE TABLE memberCopy(
	memberID int,
	copyID int,
	foreign key (memberID) references member(memberID),
	foreign key (copyID) references copy(copyID),
	primary key(memberID, copyID),
	memCopyStatus varchar(10) check(memCopyStatus in('held','checkedOut')) not null,
	createdDate date not null,
	expiryDate date not null

);

--Statemenets for stored procedures
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


