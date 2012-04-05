#CAS Authentication for Web Advisor

##Intro
This project is based on [Single Sign On to WebAdvisor Using CAS, ClearPass, and a Custom Java Filter]
(https://wiki.jasig.org/display/CASC/Single+Sign+On+to+WebAdvisor+Using+CAS%2C+ClearPass%2C+and+a+Custom+Java+Filter), 
developed by Adam Rybicki. 

The code hosted in this repository only provides the basic project template that will execute the maven 
overlay, adding the extra WebAdvisor filter and web.xml entries onto the target WebAdvisor WAR file. 

##Configuration
*Place the WebAdvisor WAR file inside the lib directory of the project. The build process assumes 
this file is so named `WebAdvisor.war`. 

*Extract the `WebAdvisor\WEB-INF\web.xml` file from the `WebAdvisor.war` file and place it inside the 
`cas-webadvisor\src\main\webapp` directory. 

*Add the following filter entries near the bottom and just above the </web-app> tag.

```xml
<filter>
    <filter-name>CAS Authentication Filter</filter-name>
    <filter-class>org.jasig.cas.client.authentication.AuthenticationFilter</filter-class>
    <init-param>
      <param-name>casServerLoginUrl</param-name>
      <param-value>https://login.esc.edu/cas/login</param-value>
    </init-param>
    <init-param>
      <param-name>serverName</param-name>
      <param-value>https://webadvsrv.esc.edu</param-value>
    </init-param>
  </filter>
   
  <filter>
    <filter-name>CAS Validation Filter</filter-name>
    <filter-class>org.jasig.cas.client.validation.Cas20ProxyReceivingTicketValidationFilter</filter-class>
    <init-param>
      <param-name>casServerUrlPrefix</param-name>
      <param-value>https://login.esc.edu/cas</param-value>
    </init-param>
    <init-param>
      <param-name>serverName</param-name>
      <param-value>https://webadvsrv.esc.edu</param-value>
    </init-param>
    <init-param>
      <param-name>proxyCallbackUrl</param-name>
      <param-value>https://webadvsrv.esc.edu/DEVSSOPO/CasProxyServlet</param-value>
    </init-param>
    <init-param>
      <param-name>proxyReceptorUrl</param-name>
      <param-value>/CasProxyServlet</param-value>
    </init-param>
  </filter>
   
  <filter>
    <filter-name>WebAdvisor Authentication Filter</filter-name>
    <filter-class>edu.esc.cas.client.webadvisor.filter.WebAdvisorLoginFilter</filter-class>
    <init-param>
      <param-name>log4jLocation</param-name>
      <param-value>classpath:log4j.xml</param-value>
    </init-param>
    <init-param>
      <param-name>clearPassURL</param-name>
      <param-value>https://login.esc.edu/cas/clearPass</param-value>
    </init-param>
    <init-param>
      <param-name>loginPattern</param-name>
      <param-value>SS=LGRQ</param-value>  <!-- This is what the filter uses to trigger redirect to CAS authentication -->
    </init-param>
    <init-param>
      <param-name>logoutPattern</param-name>
      <param-value>pid=UT-LORQ</param-value>  <!-- This is what the filter uses to trigger removal of CAS assertion -->
    </init-param>
    <init-param>
      <param-name>loginURI</param-name>
      <param-value>/DEVSSOPO/Login</param-value>  <!-- This is where the filter redirects to to perform authentication -->
    </init-param>
    <init-param>
      <param-name>webadvisorSSOURL</param-name>
      <param-value>https%3A%2F%2Fwebadvsrv.esc.edu%2FDEVSSOPO%2Fsso%3FCONSTITUENCY%3DWBST%26type%3DP%26pid%3DST-XWESTGRADE</param-value>  <!-- This is the endpoint to which the request for user's SSO token will be sent -->
    </init-param>
    <init-param>
      <param-name>webadvisorLoginURL</param-name>
      <param-value>https%3A%2F%2Fwebadvsrv.esc.edu%2FDEVSSOPO%2Fst%3FCONSTITUENCY%3DWBST%26type%3DP%26pid%3DST-XWESTGRADE</param-value>  <!-- This is where the SSO token will be sent for authentication -->
    </init-param>
    <init-param>
      <param-name>mainMenuURI</param-name>
      <param-value>/DEVSSOPO/</param-value>  <!-- This is where the filter redirects to after WebAdvisor login -->
    </init-param>
    <init-param>
      <param-name>browserHeadersToForward</param-name>  <!-- Forward these HTTP headers with the WebAdvisor login -->
      <param-value>cookie host user-agent accept accept-language accept-encoding accept-charset</param-value>
    </init-param>
    <init-param>
      <param-name>webAdvisorHeadersToForward</param-name>  <!-- Return these WebAdvisor HTTP headers after authentication -->
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
   
  <filter-mapping>
    <filter-name>WebAdvisor Authentication Filter</filter-name>  <!-- This filter must come AFTER the Jasig CAS Client filters above -->
    <url-pattern>/*</url-pattern>
  </filter-mapping>

```

*Adjust the URLs for the following filters:
-CAS Authentication Filter
Adjust the `casServerLoginUrl` and `serverName` values.

-CAS Validation Filter
Adjust the `casServerLoginUrl` and `serverName` values.

-WebAdvisor Authentication Filter
Decode the `webadvisorSSOURL`, adjust the address and encode the URL again.
Decode the `webadvisorLoginURL`, adjust the address and encode the URL again.


##Build

##Logging
