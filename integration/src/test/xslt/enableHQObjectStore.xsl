<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:t="urn:jboss:domain:transactions:1.2">

    <!--
        An XSLT style sheet which will enable HornetQ journal base object store for JBossTS,
        by adding the use-hornetq-store attribute to the transactions subsystem.
    -->
    <!-- traverse the whole tree, so that all elements and attributes are eventually current node -->
    <xsl:template match="node()|@*">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="//t:subsystem">
        <xsl:choose>
            <xsl:when test="not(//t:subsystem/t:use-hornetq-store)">
                <xsl:copy>
                    <xsl:apply-templates select="node()|@*"/>
                    <t:use-hornetq-store/>
                </xsl:copy>
            </xsl:when>
            <xsl:otherwise>
                <xsl:copy>
                    <xsl:apply-templates select="node()|@*"/>
                </xsl:copy>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
</xsl:stylesheet>
