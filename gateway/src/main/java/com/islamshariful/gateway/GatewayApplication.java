package com.islamshariful.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * TenantBase edge gateway.
 *
 * <p>One address for the platform: clients reach auth-service and user-service through here and never
 * learn the topology behind it. The servlet gateway rather than the reactive one, because both
 * services are servlet-based with virtual threads enabled -- and virtual threads are what removed the
 * reason to introduce a second programming model just to proxy without blocking a platform thread.
 *
 * <p>It does <strong>not</strong> validate tokens. Each service validates its own against
 * auth-service's published key, which keeps them safe when reached directly and avoids a gateway that
 * is the single thing standing between the internet and the data.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

}
