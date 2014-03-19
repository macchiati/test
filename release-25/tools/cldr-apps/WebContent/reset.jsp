<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8" import="org.unicode.cldr.web.*"%>
<%
String email = request.getParameter("email");
if(email==null&&email.isEmpty()) {
	response.sendRedirect(request.getContextPath()+"/survey#err_noemail");
}
email = email.trim().toLowerCase();
String s = request.getParameter("s");
if(s==null&&s.isEmpty()) {
	response.sendRedirect(request.getContextPath()+"/survey#err_nosession");
}
CookieSession cs = CookieSession.retrieve(s);
if(cs==null) {
	response.sendRedirect(request.getContextPath()+"/survey#err_badsession");
	return;
}
if(cs.user!=null) {
    response.sendRedirect(request.getContextPath()+"/survey#err_alreadyloggedin");
    return;
}
if(email.contains("admin@")) {
    response.sendRedirect(request.getContextPath()+"/survey#err_badreq");
    return;
}

Integer sumAnswer = (Integer)cs.stuff.get("sumAnswer");

String userAnswer = request.getParameter("sumAnswer");

int hashA = (int)(Math.random()*11.0);
int hashB = (int)(Math.random()*11.0);
int hashC = hashA+hashB;

%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <link rel='stylesheet' type='text/css' href='./surveytool.css' />

<title>SurveyTool | Password Reset | <%= email %></title>
</head>
<body>
<img src='STLogo.png' align='right' />
<h3>SurveyTool | Password Reset | <%= email %></h3>

<%
// did they get it right?
if(userAnswer!=null&&(sumAnswer==Integer.parseInt(userAnswer))) {
%>
  <b>Attempting to reset password:</b> <%= cs.sm.reg.resetPassword(email, WebContext.userIP(request)) %>

<hr>

If the email address on file is correct, your new password should be on its way. Check your inbox. If you have difficulty still, contact the person who set up your account.

<%
} else {
	// put it in the hash
	cs.stuff.put("sumAnswer",new Integer(hashC));
	
	if(userAnswer!=null) {
%>
	<i class='ferrorbox'>Sorry, that answer was wrong.</i><br/>
<%  } %>

	<div class='graybox'>
		To reset your password, please solve this simple math problem:  What is the sum of 
			<%= hashA %>
				+
			<%= hashB %>
				?
				
		<% if(SurveyMain.isUnofficial() ) { %><i>Hint:  <%= hashC %></i> <% } %> <br/>
	
		<form method='POST' action='<%= request.getContextPath()+request.getServletPath() %>'>
			<input name='email' type='hidden' value='<%= email %>'/>
			<input name='s' type='hidden' value='<%= s %>'/>
			<input name='sumAnswer' size=10 value='' />
			<input type='submit' value='Submit'/>
		</form>	
		
	</div>
<%
}
%>
<hr>
<a href='./survey'>Return to the Survey Tool</a>

</body>
</html>