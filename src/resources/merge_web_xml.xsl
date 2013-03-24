<?xml version="1.0"?>
<!--
  ~ Copyright 2009 Kantega AS
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~    http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<xsl:stylesheet
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="1.0">
    <xsl:param name="doc"/>
    <xsl:output method="xml" indent="yes" />
    <xsl:template match="/web-app">

        <web-app xmlns="http://java.sun.com/xml/ns/j2ee" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:schemaLocation="http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd" version="2.4">

	  <xsl:comment>  This web.xml is an created automatically by merging
  the web.xml from Aksess Publisering with the one
  from the Aksess project</xsl:comment>

            <xsl:copy-of select="$doc/display-name"/>
            <xsl:copy-of select="$doc/description"/>

	    <xsl:comment>Context params from the project have precedence</xsl:comment>
            <xsl:copy-of select="$doc/context-param"/>

            <xsl:for-each select="context-param">
	      <xsl:variable name="pname"     select="param-name"/>
              <xsl:if test="not($doc/context-param/param-name[text()=$pname])">
                    <xsl:copy-of select="."/>
                </xsl:if>
            </xsl:for-each>

	    <xsl:comment>Project filters</xsl:comment>
            <xsl:copy-of select="$doc/filter"/>
	    <xsl:comment>Aksess Publisering filters</xsl:comment>
            <xsl:copy-of select="filter"/>

      <xsl:comment>AP filter mappings</xsl:comment>
                  <xsl:copy-of select="filter-mapping"/>

      <xsl:comment>Project filter-mappings</xsl:comment>
            <xsl:copy-of select="$doc/filter-mapping"/>

      <xsl:comment>Project listeners</xsl:comment>
            <xsl:copy-of select="$doc/listener"/>

      <xsl:comment>Aksess publisering listeners</xsl:comment>
            <xsl:copy-of select="listener"/>

      <xsl:comment>Project servlets</xsl:comment>
            <xsl:copy-of select="$doc/servlet"/>
	    <xsl:comment>AP servlets</xsl:comment>
            <xsl:copy-of select="servlet"/>

	    <xsl:comment>Project servlet-mappings</xsl:comment>
            <xsl:copy-of select="$doc/servlet-mapping"/>
	    <xsl:comment>Aksess servlet-mappings</xsl:comment>
            <xsl:copy-of select="servlet-mapping"/>

            <welcome-file-list>
	      <xsl:comment>Project welcome files</xsl:comment>
                <xsl:copy-of select="$doc/welcome-file-list/welcome-file"/>
		<xsl:comment>AP welcome files</xsl:comment>
                <xsl:copy-of select="welcome-file-list/welcome-file"/>
            </welcome-file-list>

            <xsl:choose>
                <xsl:when test="$doc/session-config">
		  <xsl:comment>Session-timeout from project</xsl:comment>
                    <xsl:copy-of select="$doc/session-config"/>
                </xsl:when>
                <xsl:otherwise>
		  <xsl:comment>Aksess session timeout</xsl:comment>
                    <xsl:copy-of select="session-config"/>
                </xsl:otherwise>
            </xsl:choose>

	    <xsl:comment>Project error pages</xsl:comment>
            <xsl:copy-of select="$doc/error-page"/>
	    <xsl:comment>Aksess error pages</xsl:comment>
            <xsl:copy-of select="error-page"/>

        <xsl:comment>Project jsp-config</xsl:comment>
            <xsl:copy-of select="$doc/jsp-config"/>


        </web-app>
    </xsl:template>
</xsl:stylesheet>
