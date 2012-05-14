 #CAS Authentication for Web Advisor

##Intro
This project is based on [Single Sign On to WebAdvisor Using CAS, ClearPass, and a Custom Java Filter]
(https://wiki.jasig.org/display/CASC/Single+Sign+On+to+WebAdvisor+Using+CAS%2C+ClearPass%2C+and+a+Custom+Java+Filter), 
developed by Adam Rybicki. 

The code hosted in this repository only provides the basic project template that will execute the maven 
overlay, adding the extra WebAdvisor filter and web.xml entries onto the target WebAdvisor WAR file. 

##Configuration
* Place the WebAdvisor WAR file inside the lib directory of the project. (Create one at `cas-webadvisor\lib`).
The build process assumes this file is so named `WebAdvisor.war`. 

* Refer to the `WebAdvisor Installation and Administration` documentation, section `Setting Up a Portal Instance` 
to understand how to configure the `SERVLET_ID` parameters and the configuration of the `SingleSignOn` 
servlet mappings. Essentially, you'll have to assign a value to the `SERVLET_ID` parameter of the `SingleSignOn` 
that matches the value defined for the main `WebAdvisor` servlet. Then, define a `<url-pattern>` for the `SingleSignOn` 
servlet that will be used as the `WebAdvisorSSOUrl` below. (i.e. `/SSOWebAdvisor`)

* Extract a copy of the `WebAdvisor\WEB-INF\web.xml` file from the `WebAdvisor.war` file and place it inside the 
`cas-webadvisor\src\main\webapp\WEB-INF` directory. 

* Add the following filter entries near the bottom and just above the `</web-app>` tag. You could use a service like the
[URL Decoder/Encoder](http://meyerweb.com/eric/tools/dencoder/) to encode/decode URLs when needed.

```xml
<filter>
    <filter-name>CAS Authentication Filter</filter-name>
    <filter-class>org.jasig.cas.client.authentication.AuthenticationFilter</filter-class>
    
    <!-- Change the param-value for your CAS server! -->
    <init-param>
      <param-name>casServerLoginUrl</param-name>
      <param-value>https://login.esc.edu/cas/login</param-value>
    </init-param>
    
    <!-- Change the param-value for your WebAdvisor server! -->
    <init-param>
      <param-name>serverName</param-name>
      <param-value>https://webadvsrv.esc.edu</param-value>
    </init-param>
  </filter>
   
  <filter>
    <filter-name>CAS Validation Filter</filter-name>
    <filter-class>org.jasig.cas.client.validation.Cas20ProxyReceivingTicketValidationFilter</filter-class>
    
    <!-- Change the param-value for your CAS server! -->
    <init-param>
      <param-name>casServerUrlPrefix</param-name>
      <param-value>https://login.esc.edu/cas</param-value>
    </init-param>
    
    <!-- Change the param-value for your WebAdvisor server! -->
    <init-param>
      <param-name>serverName</param-name>
      <param-value>https://webadvsrv.esc.edu</param-value>
    </init-param>
    
    <!-- Change the param-value for your WebAdvisor server! 
         Also, the URL assumes the context path `WebAdvisor` which
         you may have to change.
    -->
    <init-param>
      <param-name>proxyCallbackUrl</param-name>
      <param-value>https://webadvsrv.esc.edu/WebAdvisor/CasProxyServlet</param-value>
    </init-param>
    
    <init-param>
      <param-name>proxyReceptorUrl</param-name>
      <param-value>/CasProxyServlet</param-value>
    </init-param>
    
    <init-param>
      <param-name>allowAnyProxy</param-name>
      <param-value>true</param-value>
    </init-param>
  </filter>
   
  <filter>
    <filter-name>WebAdvisor Authentication Filter</filter-name>
    <filter-class>edu.esc.cas.client.webadvisor.filter.WebAdvisorLoginFilter</filter-class>
    <init-param>
      <param-name>log4jLocation</param-name>
      <param-value>classpath:log4j.xml</param-value>
    </init-param>
    
    <!-- Change the param-value for your CAS server! -->
    <init-param>
      <param-name>clearPassURL</param-name>
      <param-value>https://login.esc.edu/cas/clearPass</param-value>
    </init-param>
    
    <!-- This is what the filter uses to trigger redirect to CAS authentication -->
    <init-param>
      <param-name>loginPattern</param-name>
      <param-value>SS=LGRQ</param-value>  
    </init-param>
    
    <!-- This is what the filter uses to trigger removal of CAS assertion -->
    <init-param>
      <param-name>logoutPattern</param-name>
      <param-value>pid=UT-LORQ</param-value>  
    </init-param>
    
     <!-- This is where the filter redirects to to perform authentication -->
    <init-param>
      <param-name>loginURI</param-name>
      <param-value>/WebAdvisor/Login</param-value> 
    </init-param>
    
    <!-- 
         Decode the URL, change the param-value for your WebAdvisor server!
         and encode it back. 
         
         Also, the URL assumes the context path `WebAdvisor` which
         you may have to change.
    -->
    <init-param>
      <param-name>webadvisorSSOURL</param-name>
      <param-value>https%3A%2F%2Fwebadvsrv.esc.edu%2FWebAdvisor%2Fsso%3FCONSTITUENCY%3DWBST%26type%3DP%26pid%3DST-XWESTGRADE</param-value>  <!-- This is the endpoint to which the request for user's SSO token will be sent -->
    </init-param>
    
    <!-- 
         Decode the URL, change the param-value for your WebAdvisor server!
         and encode it back. 
         
         Also, the URL assumes the context path `WebAdvisor` which
         you may have to change.
    -->
    <init-param>
      <param-name>webadvisorLoginURL</param-name>
      <param-value>https%3A%2F%2Fwebadvsrv.esc.edu%2FWebAdvisor%2Fst%3FCONSTITUENCY%3DWBST%26type%3DP%26pid%3DST-XWESTGRADE</param-value>  <!-- This is where the SSO token will be sent for authentication -->
    </init-param>
    
    <!-- This is where the filter redirects to after WebAdvisor login -->
    <init-param>
      <param-name>mainMenuURI</param-name>
      <param-value>/WebAdvisor/</param-value>  
    </init-param>
    
    <!-- Forward these HTTP headers with the WebAdvisor login -->
    <init-param>
      <param-name>browserHeadersToForward</param-name>  
      <param-value>cookie host user-agent accept accept-language accept-encoding accept-charset</param-value>
    </init-param>
    
     <!-- Return these WebAdvisor HTTP headers after authentication -->
    <init-param>
      <param-name>webAdvisorHeadersToForward</param-name> 
      <param-value>Set-Cookie Content-Type Content-Length</param-value>
    </init-param>
  </filter>
   
  <filter-mapping>
    <filter-name>CAS Validation Filter</filter-name>
    <url-pattern>/CasProxyServlet</url-pattern>
  </filter-mapping>
 
  <filter-mapping>
    <filter-name>CAS Authentication Filter</filter-name>
    <url-pattern>/Login</url-pattern>
  </filter-mapping>
   
  <filter-mapping>
    <filter-name>CAS Validation Filter</filter-name>
    <url-pattern>/Login</url-pattern>
  </filter-mapping>
   
  <!-- This filter must come AFTER the Jasig CAS Client filters above --> 
  <filter-mapping>
    <filter-name>WebAdvisor Authentication Filter</filter-name>  
    <url-pattern>/*</url-pattern>
  </filter-mapping>

```

* Adjust the URLs for the following filters:

1. CAS Authentication Filter: Adjust the `casServerLoginUrl` and `serverName` values.

2. CAS Validation Filter: Adjust the `casServerLoginUrl` and `serverName` values.

3. WebAdvisor Authentication Filter: 
    1. Decode the `webadvisorSSOURL`, adjust the address and encode the URL again.
    2. Decode the `webadvisorLoginURL`, adjust the address and encode the URL again.


##Build
On the command line, navigate to the project directory and execute the maven command: `mvn clean package`.

Remove all previous deployments/copies of the Web Advisor WAR file and its deployment directory inside `webapps`. You
may also want to remove the Tomcat `work` directory and clear out all the logs to start fresh. 

Copy the `WebAdvisor.war` file from the `target` directory and place it into Tomcat's `webapps` folder.

Start Tomcat. 

##Logging
See the `log4j.xml` file inside the  `WebAdvisor\WEB-INF\classes` directory.
