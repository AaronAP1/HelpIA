package pe.com.pacifico.helpia.function;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import pe.com.pacifico.helpia.application.AccountResolver;
import pe.com.pacifico.helpia.application.DefaultAccountScopePolicy;
import pe.com.pacifico.helpia.application.EmailValidator;
import pe.com.pacifico.helpia.application.OperationRequestParser;
import pe.com.pacifico.helpia.application.RateLimitService;
import pe.com.pacifico.helpia.application.UserLookupService;
import pe.com.pacifico.helpia.auth.JwtValidator;
import pe.com.pacifico.helpia.common.Env;
import pe.com.pacifico.helpia.config.AutomationSettings;
import pe.com.pacifico.helpia.config.CallbackSettings;
import pe.com.pacifico.helpia.config.EmailDomainSettings;
import pe.com.pacifico.helpia.config.JwtSettings;
import pe.com.pacifico.helpia.config.RateLimitSettings;
import pe.com.pacifico.helpia.config.ServiceEndpoints;
import pe.com.pacifico.helpia.config.SyncWaitSettings;
import pe.com.pacifico.helpia.domain.OrchestrationTimings;
import pe.com.pacifico.helpia.infrastructure.automation.AutomationClient;
import pe.com.pacifico.helpia.infrastructure.callback.CallbackClient;
import pe.com.pacifico.helpia.infrastructure.graph.GraphUserDirectory;
import pe.com.pacifico.helpia.infrastructure.identity.AzureTokenProvider;
import pe.com.pacifico.helpia.infrastructure.storage.TableRateLimitStore;

/**
 * Composición de dependencias (sin Spring, para no penalizar el arranque en frío).
 * Las piezas se crean la primera vez que se usan y se comparten por proceso: así health y validar-existencia
 * funcionan aunque Automation o el callback todavía no estén configurados (Etapa 2 antes que Etapa 3).
 */
public final class Dependencies {

    private static final Dependencies INSTANCE = new Dependencies(Env.system(), Clock.systemUTC());

    private final Env env;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final Lazy<JwtValidator> jwtValidator;
    private final Lazy<ServiceEndpoints> endpoints;
    private final Lazy<AzureTokenProvider> tokens;
    private final Lazy<GraphUserDirectory> directory;
    private final Lazy<EmailDomainSettings> emailDomains;
    private final Lazy<EmailValidator> emailValidator;
    private final Lazy<UserLookupService> userLookup;
    private final Lazy<AccountResolver> accountResolver;
    private final Lazy<RateLimitService> rateLimitService;
    private final Lazy<AutomationSettings> automationSettings;
    private final Lazy<AutomationClient> automationClient;
    private final Lazy<CallbackClient> callbackClient;
    private final OperationRequestParser operationRequestParser = new OperationRequestParser();

    Dependencies(Env env, Clock clock) {
        this.env = env;
        this.clock = clock;
        this.jwtValidator = new Lazy<>(() -> JwtValidator.forEntraId(JwtSettings.load(env)));
        this.endpoints = new Lazy<>(() -> ServiceEndpoints.load(env));
        this.tokens = new Lazy<>(() -> AzureTokenProvider.fromEnvironment(env));
        this.directory = new Lazy<>(() -> new GraphUserDirectory(http, tokens.get(), endpoints.get().graphBaseUrl()));
        this.emailDomains = new Lazy<>(() -> EmailDomainSettings.load(env));
        this.emailValidator = new Lazy<>(() -> new EmailValidator(emailDomains.get().allowedDomains()));
        this.userLookup = new Lazy<>(() -> new UserLookupService(directory.get()));
        this.accountResolver = new Lazy<>(() -> new AccountResolver(
                directory.get(), new DefaultAccountScopePolicy(emailDomains.get().allowedDomains())));
        this.rateLimitService = new Lazy<>(() -> {
            RateLimitSettings settings = RateLimitSettings.load(env);
            TableRateLimitStore store = settings.enabled()
                    ? TableRateLimitStore.create(settings, tokens.get().credential())
                    : null;
            return new RateLimitService(settings, store, clock);
        });
        this.automationSettings = new Lazy<>(() -> AutomationSettings.load(env));
        this.automationClient = new Lazy<>(() -> new AutomationClient(
                http, tokens.get(), automationSettings.get(), endpoints.get().armBaseUrl()));
        this.callbackClient = new Lazy<>(() -> new CallbackClient(http, CallbackSettings.load(env), clock));
    }

    public static Dependencies get() {
        return INSTANCE;
    }

    public Clock clock() {
        return clock;
    }

    public JwtValidator jwtValidator() {
        return jwtValidator.get();
    }

    public EmailValidator emailValidator() {
        return emailValidator.get();
    }

    public UserLookupService userLookup() {
        return userLookup.get();
    }

    public OperationRequestParser operationRequestParser() {
        return operationRequestParser;
    }

    public AccountResolver accountResolver() {
        return accountResolver.get();
    }

    public RateLimitService rateLimitService() {
        return rateLimitService.get();
    }

    public AutomationSettings automationSettings() {
        return automationSettings.get();
    }

    public AutomationClient automationClient() {
        return automationClient.get();
    }

    public CallbackClient callbackClient() {
        return callbackClient.get();
    }

    public OrchestrationTimings orchestrationTimings() {
        return OrchestrationTimings.load(env);
    }

    public SyncWaitSettings syncWaitSettings() {
        return SyncWaitSettings.load(env);
    }
}
