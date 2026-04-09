package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;

/**
 * LDAP認証設定
 *
 * <p>app.ldap.enabled=true のときのみ有効になる。
 * 開発環境では spring.ldap.embedded.* による組み込みLDAPサーバーが自動起動する。
 * 本番環境では spring.ldap.urls / base / username / password に実際のLDAPサーバー情報を設定すること。
 */
@Configuration
@ConditionalOnProperty(name = "app.ldap.enabled", havingValue = "true")
public class LdapConfig {

    @Value("${spring.ldap.urls:ldap://localhost:8389}")
    private String ldapUrl;

    @Value("${spring.ldap.base:dc=example,dc=com}")
    private String ldapBase;

    @Value("${spring.ldap.username:}")
    private String ldapManagerDn;

    @Value("${spring.ldap.password:}")
    private String ldapManagerPassword;

    /** ユーザー検索のベースDN (ldapBase からの相対パス) */
    @Value("${app.ldap.user-search-base:ou=users}")
    private String userSearchBase;

    /** ユーザー検索フィルター。{0} がログイン名に置換される */
    @Value("${app.ldap.user-search-filter:(uid={0})}")
    private String userSearchFilter;

    /** グループ検索のベースDN (ldapBase からの相対パス) */
    @Value("${app.ldap.group-search-base:ou=groups}")
    private String groupSearchBase;

    /** グループ検索フィルター。{0} がユーザーDNに置換される */
    @Value("${app.ldap.group-search-filter:(member={0})}")
    private String groupSearchFilter;

    @Bean
    public LdapContextSource ldapContextSource() {
        LdapContextSource ctx = new LdapContextSource();
        ctx.setUrl(ldapUrl);
        ctx.setBase(ldapBase);
        if (ldapManagerDn != null && !ldapManagerDn.isBlank()) {
            ctx.setUserDn(ldapManagerDn);
            ctx.setPassword(ldapManagerPassword);
        }
        return ctx;
    }

    /**
     * LDAP認証プロバイダー
     *
     * <ul>
     *   <li>BindAuthenticator: ユーザーDNを検索しバインド認証を行う</li>
     *   <li>DefaultLdapAuthoritiesPopulator: グループメンバーシップからROLEを付与する</li>
     * </ul>
     */
    @Bean
    public LdapAuthenticationProvider ldapAuthenticationProvider(LdapContextSource ldapContextSource) {
        FilterBasedLdapUserSearch userSearch =
                new FilterBasedLdapUserSearch(userSearchBase, userSearchFilter, ldapContextSource);

        BindAuthenticator authenticator = new BindAuthenticator(ldapContextSource);
        authenticator.setUserSearch(userSearch);

        DefaultLdapAuthoritiesPopulator authoritiesPopulator =
                new DefaultLdapAuthoritiesPopulator(ldapContextSource, groupSearchBase);
        authoritiesPopulator.setGroupSearchFilter(groupSearchFilter);
        authoritiesPopulator.setRolePrefix("ROLE_");
        authoritiesPopulator.setSearchSubtree(false);
        authoritiesPopulator.setConvertToUpperCase(true);

        return new LdapAuthenticationProvider(authenticator, authoritiesPopulator);
    }
}
