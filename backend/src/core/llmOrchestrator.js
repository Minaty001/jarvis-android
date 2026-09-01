import { CONFIG } from '../config.js';

class LLMProvider {
  constructor(name, baseUrl, apiKey, model, headers = {}) {
    this.name = name;
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.model = model;
    this.headers = headers;
    this.lastUsed = 0;
    this.errorCount = 0;
  }

  async complete(messages, tools = null) {
    const body = {
      model: this.model,
      messages,
      temperature: 0.7,
      max_tokens: 2048,
    };
    if (tools) body.tools = tools;

    const response = await fetch(`${this.baseUrl}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${this.apiKey}`,
        ...this.headers,
      },
      body: JSON.stringify(body),
    });

    if (response.status === 429) {
      this.errorCount++;
      throw new RateLimitError(`${this.name} rate limited`);
    }
    if (!response.ok) {
      const err = await response.text();
      throw new Error(`${this.name} error ${response.status}: ${err}`);
    }

    const data = await response.json();
    this.lastUsed = Date.now();
    this.errorCount = 0;
    if (!data.choices || data.choices.length === 0) {
      throw new Error(`${this.name}: empty choices in response`);
    }
    return data.choices[0].message;
  }
}

class RateLimitError extends Error {
  constructor(msg) {
    super(msg);
    this.name = 'RateLimitError';
  }
}

export class LLMOrchestrator {
  constructor() {
    this.providers = [];
    this.currentIndex = 0;
    this._initProviders();
  }

  _initProviders() {
    if (CONFIG.groqApiKey) {
      this.providers.push(
        new LLMProvider(
          'groq',
          'https://api.groq.com/openai/v1',
          CONFIG.groqApiKey,
          'llama3-70b-8192'
        )
      );
    }

    if (CONFIG.openrouterApiKey) {
      this.providers.push(
        new LLMProvider(
          'openrouter',
          'https://openrouter.ai/api/v1',
          CONFIG.openrouterApiKey,
          'meta-llama/llama-3.3-70b-instruct:free',
          { 'HTTP-Referer': 'https://jarvis-ai.app', 'X-Title': 'JARVIS AI' }
        )
      );
    }

    if (CONFIG.nvidiaNimApiKey) {
      this.providers.push(
        new LLMProvider(
          'nvidia-nim',
          'https://integrate.api.nvidia.com/v1',
          CONFIG.nvidiaNimApiKey,
          'nvidia/llama-3.1-nemotron-70b-instruct'
        )
      );
    }

    if (this.providers.length === 0) {
      console.warn('No LLM providers configured. Set API keys in .env');
    }
  }

  async generate(messages, tools = null) {
    if (this.providers.length === 0) {
      throw new Error('No LLM providers available');
    }

    const totalProviders = this.providers.length;
    for (let i = 0; i < totalProviders; i++) {
      const idx = (this.currentIndex + i) % totalProviders;
      const provider = this.providers[idx];
      try {
        const response = await provider.complete(messages, tools);
        this.currentIndex = (idx + 1) % totalProviders;
        return { ...response, provider: provider.name };
      } catch (err) {
        console.error(`LLM ${provider.name} failed:`, err.message);
        if (err instanceof RateLimitError) continue;
        continue;
      }
    }
    throw new Error('All LLM providers exhausted');
  }

  getProviderStatus() {
    return this.providers.map(p => ({
      name: p.name,
      model: p.model,
      lastUsed: p.lastUsed,
      errorCount: p.errorCount,
    }));
  }
}
