package br.com.rinos.app.backend.module.storage.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;
import br.com.rinos.app.backend.module.storage.vo.TenantSchemaInitializationResultVO;

class TenantSchemaInitializerTest {

  @Test
  void initialize_shouldCreateAndInitializeSchema_whenPhysicalSchemaIsAbsent() throws Exception {
    DataSource globalDataSource = mock(DataSource.class);
    Connection globalConnection = mock(Connection.class);
    PreparedStatement existenceQuery = mock(PreparedStatement.class);
    ResultSet absent = mock(ResultSet.class);
    Statement statement = mock(Statement.class);
    TenantDataSourceFactory factory = mock(TenantDataSourceFactory.class);
    HikariDataSource tenantDataSource = mock(HikariDataSource.class);
    TenantSchemaInitScriptComponent scripts = mock(TenantSchemaInitScriptComponent.class);
    TenantDatabaseStructureVerifier verifier = mock(TenantDatabaseStructureVerifier.class);
    TenantPhysicalIdentifier identifier = new TenantPhysicalIdentifier("0123456789abcdef0123456789abcdef");
    when(globalDataSource.getConnection()).thenReturn(globalConnection);
    when(globalConnection.prepareStatement(any())).thenReturn(existenceQuery);
    when(existenceQuery.executeQuery()).thenReturn(absent);
    when(absent.next()).thenReturn(false);
    when(globalConnection.createStatement()).thenReturn(statement);
    when(factory.create(identifier)).thenReturn(tenantDataSource);
    TenantSchemaInitializer initializer = new TenantSchemaInitializer(globalDataSource, factory, scripts, verifier);

    TenantSchemaInitializationResultVO result = initializer.initialize(identifier, "20260829001");

    assertThat(result.createdNow()).isTrue();
    verify(statement).execute("CREATE DATABASE `rinos_0123456789abcdef0123456789abcdef` CHARACTER SET utf8mb4"
        + " COLLATE utf8mb4_unicode_ci");
    verify(scripts).execute(tenantDataSource);
    verify(verifier).verify(eq(tenantDataSource), eq("20260829001"), eq(java.util.List.of()));
    verify(tenantDataSource).close();
  }

  @Test
  void initialize_shouldValidateWithoutRepeatingInit_whenPhysicalSchemaAlreadyExists() throws Exception {
    DataSource globalDataSource = mock(DataSource.class);
    Connection globalConnection = mock(Connection.class);
    PreparedStatement existenceQuery = mock(PreparedStatement.class);
    ResultSet present = mock(ResultSet.class);
    TenantDataSourceFactory factory = mock(TenantDataSourceFactory.class);
    HikariDataSource tenantDataSource = mock(HikariDataSource.class);
    TenantSchemaInitScriptComponent scripts = mock(TenantSchemaInitScriptComponent.class);
    TenantDatabaseStructureVerifier verifier = mock(TenantDatabaseStructureVerifier.class);
    TenantPhysicalIdentifier identifier = new TenantPhysicalIdentifier("fedcba9876543210fedcba9876543210");
    when(globalDataSource.getConnection()).thenReturn(globalConnection);
    when(globalConnection.prepareStatement(any())).thenReturn(existenceQuery);
    when(existenceQuery.executeQuery()).thenReturn(present);
    when(present.next()).thenReturn(true);
    when(factory.create(identifier)).thenReturn(tenantDataSource);
    TenantSchemaInitializer initializer = new TenantSchemaInitializer(globalDataSource, factory, scripts, verifier);

    TenantSchemaInitializationResultVO result = initializer.initialize(identifier, "20260829001");

    assertThat(result.createdNow()).isFalse();
    verify(scripts, never()).execute(tenantDataSource);
    verify(verifier).verify(eq(tenantDataSource), eq("20260829001"), eq(java.util.List.of()));
    verify(tenantDataSource).close();
  }
}
