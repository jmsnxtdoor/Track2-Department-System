import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'api_client.dart';

class LoginPage extends StatefulWidget {
  const LoginPage({
    required this.apiBaseUrl,
    required this.onSignedIn,
    this.clientFactory,
    super.key,
  });

  final String apiBaseUrl;
  final ValueChanged<StudentApiClient> onSignedIn;
  final http.Client Function()? clientFactory;

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _apiUrl;
  final _username = TextEditingController();
  final _password = TextEditingController();
  String? _error;
  bool _loading = false;
  bool _showPassword = false;

  @override
  void initState() {
    super.initState();
    _apiUrl = TextEditingController(text: widget.apiBaseUrl);
  }

  @override
  void dispose() {
    _apiUrl.dispose();
    _username.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _signIn() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final client = await StudentApiClient.signIn(
        apiBaseUrl: _apiUrl.text,
        username: _username.text.trim(),
        password: _password.text,
        client: widget.clientFactory?.call(),
      );
      _password.clear();
      widget.onSignedIn(client);
    } on ApiException catch (failure) {
      if (mounted) setState(() => _error = failure.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(28),
                  child: Form(
                    key: _formKey,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        CircleAvatar(
                          radius: 28,
                          backgroundColor: theme.colorScheme.primary,
                          child: const Icon(
                            Icons.school_outlined,
                            color: Colors.white,
                            size: 30,
                          ),
                        ),
                        const SizedBox(height: 18),
                        Text(
                          'Student Portal',
                          textAlign: TextAlign.center,
                          style: theme.textTheme.headlineSmall?.copyWith(
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          'Sign in with your department student account.',
                          textAlign: TextAlign.center,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: const Color(0xFF475569),
                          ),
                        ),
                        const SizedBox(height: 24),
                        TextFormField(
                          key: const Key('api-url'),
                          controller: _apiUrl,
                          keyboardType: TextInputType.url,
                          autocorrect: false,
                          decoration: const InputDecoration(
                            labelText: 'API server URL',
                            hintText: 'http://10.0.2.2:8080/api',
                            prefixIcon: Icon(Icons.dns_outlined),
                            border: OutlineInputBorder(),
                          ),
                          validator: (value) =>
                              (value == null || value.trim().isEmpty)
                                  ? 'Enter the server URL.'
                                  : null,
                        ),
                        const SizedBox(height: 14),
                        TextFormField(
                          key: const Key('username'),
                          controller: _username,
                          textInputAction: TextInputAction.next,
                          autofillHints: const [AutofillHints.username],
                          decoration: const InputDecoration(
                            labelText: 'Username',
                            prefixIcon: Icon(Icons.person_outline),
                            border: OutlineInputBorder(),
                          ),
                          validator: (value) =>
                              (value == null || value.trim().isEmpty)
                                  ? 'Enter your username.'
                                  : null,
                        ),
                        const SizedBox(height: 14),
                        TextFormField(
                          key: const Key('password'),
                          controller: _password,
                          obscureText: !_showPassword,
                          textInputAction: TextInputAction.done,
                          autofillHints: const [AutofillHints.password],
                          onFieldSubmitted: (_) => _signIn(),
                          decoration: InputDecoration(
                            labelText: 'Password',
                            prefixIcon: const Icon(Icons.lock_outline),
                            border: const OutlineInputBorder(),
                            suffixIcon: IconButton(
                              tooltip: _showPassword
                                  ? 'Hide password'
                                  : 'Show password',
                              onPressed: () => setState(
                                  () => _showPassword = !_showPassword),
                              icon: Icon(
                                _showPassword
                                    ? Icons.visibility_off
                                    : Icons.visibility,
                              ),
                            ),
                          ),
                          validator: (value) => (value == null || value.isEmpty)
                              ? 'Enter your password.'
                              : null,
                        ),
                        const SizedBox(height: 14),
                        if (_error != null) ...[
                          Text(
                            _error!,
                            key: const Key('login-error'),
                            style: TextStyle(color: theme.colorScheme.error),
                          ),
                          const SizedBox(height: 14),
                        ],
                        FilledButton(
                          onPressed: _loading ? null : _signIn,
                          child: Padding(
                            padding: const EdgeInsets.symmetric(vertical: 12),
                            child: _loading
                                ? const SizedBox(
                                    width: 20,
                                    height: 20,
                                    child: CircularProgressIndicator(
                                        strokeWidth: 2),
                                  )
                                : const Text('Sign in'),
                          ),
                        ),
                        const SizedBox(height: 12),
                        Text(
                          'Use HTTPS outside local development.',
                          textAlign: TextAlign.center,
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: const Color(0xFF64748B),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
