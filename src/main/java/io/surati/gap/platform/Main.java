/**
MIT License

Copyright (c) 2021 Surati

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package io.surati.gap.platform;

import com.baudoliver7.jdbc.toolset.lockable.LocalLockedDataSource;
import com.minlessika.utils.ConsoleArgs;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.surati.gap.commons.utils.pf4j.DatabaseSetup;
import io.surati.gap.commons.utils.pf4j.ModuleRegistration;
import io.surati.gap.commons.utils.pf4j.WebFront;
import io.surati.gap.web.base.FkMimes;
import io.surati.gap.web.base.TkSafe;
import io.surati.gap.web.base.TkSafeUserAlert;
import io.surati.gap.web.base.TkTransaction;
import io.surati.gap.web.base.auth.SCodec;
import io.surati.gap.web.base.auth.TkAuth;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.JarPluginManager;
import org.pf4j.PluginManager;
import org.takes.facets.auth.Pass;
import org.takes.facets.auth.PsByFlag;
import org.takes.facets.auth.PsChain;
import org.takes.facets.auth.PsCookie;
import org.takes.facets.auth.PsLogout;
import org.takes.facets.flash.TkFlash;
import org.takes.facets.fork.FkChain;
import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.Fork;
import org.takes.facets.fork.TkFork;
import org.takes.facets.forward.TkForward;
import org.takes.http.Exit;
import org.takes.http.FtCli;
import org.takes.tk.TkSlf4j;

/**
 * Entry of application.
 * 
 * @since 0.1
 */
public final class Main {

	/**
	 * Local connection.
	 */
	private static final ThreadLocal<Connection> localconn = new ThreadLocal<>();

	/**
	 * App entry
	 * @param args Arguments
	 * @throws Exception If some problems in
	 */
	public static void main(String[] args) throws Exception {
		final Map<String, String> map = new ConsoleArgs("--", args).asMap();
		final HikariConfig config = new HikariConfig();
		config.setDriverClassName(map.get("db-driver"));
		config.setJdbcUrl(map.get("db-url"));
		config.setUsername(map.get("db-user"));
		config.setPassword(map.get("db-password"));
		int psize = 5;
		if(StringUtils.isNotBlank(map.get("db-pool-size"))) {
			psize = Integer.parseInt(map.get("db-pool-size"));
		}
		config.setMaximumPoolSize(psize);
		final DataSource src = new HikariDataSource(config);
		final DataSource lcksrc = new LocalLockedDataSource(src, Main.localconn);
		final Pass pass = new PsChain(
			new PsByFlag(
				new PsByFlag.Pair(
					PsLogout.class.getSimpleName(),
					new PsLogout()
				)
			),
			new PsCookie(
				new SCodec()
			)
		);
		final PluginManager manager = new JarPluginManager(Paths.get("./plugins"));
		try {
			manager.loadPlugins();
			manager.startPlugins();
			for (ModuleRegistration registration : manager.getExtensions(ModuleRegistration.class)) {
				registration.register();
			}
			for (DatabaseSetup setup : manager.getExtensions(DatabaseSetup.class)) {
				setup.migrate(src, map.containsKey("demo"));
			}
			final List<WebFront> fronts = manager.getExtensions(WebFront.class);
			if (fronts.isEmpty()) {
				throw new IllegalArgumentException("No valid plugin has been found !");
			}
			Fork pages = new FkChain();
			for (WebFront front : fronts) {
				pages = new FkChain(
					pages,
					front.pages(),
					front.pages(lcksrc),
					front.pages(lcksrc, pass)
				);
			}
			new FtCli(
				new TkSlf4j(
					new TkSafe(
						new TkForward(
							new TkFlash(
								new TkAuth(
									new TkSafeUserAlert(
										src,
										new TkTransaction(
											new TkFork(
												new FkMimes(),
												new FkRegex("/robots\\.txt", ""),
												pages
											),
											Main.localconn
										)
									),
									pass
								)
							)
						)
					)
				),
				args
			).start(Exit.NEVER);
		} finally {
			manager.stopPlugins();
			manager.unloadPlugins();
		}
	}
}
