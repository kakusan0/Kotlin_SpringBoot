package com.example.demo.service;

import com.example.demo.config.AppProperties;
import com.example.demo.config.GeoIpProperties;
import com.example.demo.config.ReportProperties;
import com.example.demo.config.WebAuthnProperties;
import com.example.demo.mapper.AccessLogMapper;
import com.example.demo.mapper.BlacklistEventMapper;
import com.example.demo.mapper.BlacklistIpMapper;
import com.example.demo.mapper.UaBlacklistRuleMapper;
import com.example.demo.mapper.WebAuthnCredentialMapper;
import com.example.demo.mapper.WhitelistIpMapper;
import com.example.demo.model.BlacklistEvent;
import com.example.demo.model.ReportJob;
import com.example.demo.model.UaBlacklistRule;
import com.example.demo.model.WebAuthnCredential;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdditionalServiceMethodsTest {

    @Mock
    private AccessLogMapper accessLogMapper;
    @Mock
    private BlacklistIpMapper blacklistIpMapper;
    @Mock
    private WhitelistIpMapper whitelistIpMapper;
    @Mock
    private BlacklistEventService blacklistEventService;

    @Test
    void autoBlacklistFiltersBlankAndDuplicateIps() {
        when(accessLogMapper.selectIpsWithMissingUserAgent())
                .thenReturn(Arrays.asList("1.1.1.1", "", "1.1.1.1", null, "2.2.2.2"));
        IpBlacklistService service = new IpBlacklistService(
                accessLogMapper, blacklistIpMapper, whitelistIpMapper, blacklistEventService);

        var result = service.autoBlacklistMissingUserAgents();

        assertEquals(2, result.totalCandidates());
        assertEquals(2, result.processed());
        verify(blacklistIpMapper).upsertIncrementTimesBulk(List.of("1.1.1.1", "2.2.2.2"));
        verify(whitelistIpMapper).markBlacklistedAndIncrementBulk(List.of("1.1.1.1", "2.2.2.2"));
        verify(blacklistEventService).recordEventsSync(any());
    }

    @Test
    void autoBlacklistDoesNothingWithoutCandidates() {
        when(accessLogMapper.selectIpsWithMissingUserAgent()).thenReturn(Arrays.asList("", null));
        IpBlacklistService service = new IpBlacklistService(
                accessLogMapper, blacklistIpMapper, whitelistIpMapper, blacklistEventService);

        assertEquals(0, service.autoBlacklistMissingUserAgents().processed());
        verify(blacklistIpMapper, never()).upsertIncrementTimesBulk(any());
        verify(blacklistEventService, never()).recordEventsSync(any());
    }

    @Test
    void blacklistEventServiceHandlesEmptyAndSyncEvents() {
        BlacklistEventMapper mapper = org.mockito.Mockito.mock(BlacklistEventMapper.class);
        BlacklistEventService service = new BlacklistEventService(mapper);
        BlacklistEvent event = new BlacklistEvent();

        service.recordEventSync(event);
        service.recordEventsSync(List.of(event));
        service.recordEventsSync(List.of());
        service.recordEvents(List.of());

        verify(mapper).insert(event);
        verify(mapper).insertBulk(List.of(event));
    }

    @Test
    void webAuthnChallengeMethodsStoreAndConsumeOnce() {
        WebAuthnCredentialMapper mapper = org.mockito.Mockito.mock(WebAuthnCredentialMapper.class);
        WebAuthnProperties properties = new WebAuthnProperties();
        WebAuthnService service = new WebAuthnService(mapper, properties);
        byte[] challenge = new byte[]{1, 2, 3};

        service.saveRegistrationChallenge("alice", challenge);
        assertArrayEquals(challenge, service.consumeRegistrationChallenge("alice"));
        assertNull(service.consumeRegistrationChallenge("alice"));

        service.saveAuthenticationChallenge("alice", challenge);
        assertArrayEquals(challenge, service.consumeAuthenticationChallenge("alice"));

        String id = service.saveDiscoverableChallenge(challenge);
        assertNotNull(id);
        assertArrayEquals(challenge, service.consumeDiscoverableChallenge(id));
        assertNull(service.consumeDiscoverableChallenge(id));
    }

    @Test
    void webAuthnCredentialMethodsDelegateToMapper() {
        WebAuthnCredentialMapper mapper = org.mockito.Mockito.mock(WebAuthnCredentialMapper.class);
        WebAuthnService service = new WebAuthnService(mapper, new WebAuthnProperties());
        WebAuthnCredential credential = new WebAuthnCredential();
        when(mapper.findByUsername("alice")).thenReturn(List.of(credential));
        when(mapper.findByCredentialId(any())).thenReturn(credential);
        when(mapper.findById(3L)).thenReturn(credential);

        assertEquals(List.of(credential), service.findCredentials("alice"));
        assertEquals(credential, service.findByCredentialId(new byte[]{1}));
        assertEquals(credential, service.findCredentialById(3L));
        service.deleteCredential(3L);

        verify(mapper).deleteById(3L);
    }

    @Test
    void uaBlacklistMatchesExactPrefixAndRegexRules() {
        UaBlacklistRuleMapper mapper = org.mockito.Mockito.mock(UaBlacklistRuleMapper.class);
        when(mapper.selectActive()).thenReturn(List.of(
                rule("EXACT", "Chrome"),
                rule("PREFIX", "Bot"),
                rule("REGEX", "crawler-[0-9]+")));
        UaBlacklistService service = new UaBlacklistService(mapper);

        assertTrue(service.matches("chrome"));
        assertTrue(service.matches("Bot/1.0"));
        assertTrue(service.matches("crawler-123"));
        assertFalse(service.matches("Firefox"));
        assertFalse(service.matches(null));
        service.evictCache();
        assertTrue(service.matches("chrome"));
    }

    @Test
    void reportJobServiceReturnsStoredJob() {
        ReportJobMapperFixture fixture = new ReportJobMapperFixture();
        ReportJob job = ReportJob.builder().id(5L).username("alice").build();
        when(fixture.mapper.selectById(5L)).thenReturn(job);
        AppProperties properties = new AppProperties();
        ReportJobService service = new ReportJobService(fixture.mapper, null, properties);

        assertEquals(job, service.getJob(5L));
    }

    @Test
    void reportServiceFormatsMinutes() {
        ReportProperties properties = new ReportProperties();
        ReportService service = new ReportService(null, null, properties);

        assertEquals("8:05", service.formatMinutesToHM(485));
        assertEquals("", service.formatMinutesToHM(null));
        assertEquals("0:00", service.formatMinutesToHM(0));
    }

    @Test
    void geoIpServiceIsDisabledWhenDatabasePathIsBlank() {
        GeoIpProperties properties = new GeoIpProperties();
        properties.setMmdbPath("");
        GeoIpCountryService service = new GeoIpCountryService(properties);

        service.init();

        assertFalse(service.isEnabled());
        assertNull(service.lookupCountryCode("127.0.0.1"));
        assertTrue(service.isAllowedCountry("127.0.0.1"));
    }

    private static UaBlacklistRule rule(String matchType, String pattern) {
        UaBlacklistRule rule = new UaBlacklistRule();
        rule.setMatchType(matchType);
        rule.setPattern(pattern);
        rule.setDeleted(false);
        return rule;
    }

    private static final class ReportJobMapperFixture {
        private final com.example.demo.mapper.ReportJobMapper mapper =
                org.mockito.Mockito.mock(com.example.demo.mapper.ReportJobMapper.class);
    }
}
