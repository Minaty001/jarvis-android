import { config } from 'dotenv';
config();

export const CONFIG = {
  port: parseInt(process.env.PORT || '10000', 10),
  nodeEnv: process.env.NODE_ENV || 'development',

  groqApiKey: process.env.GROQ_API_KEY || '',
  openrouterApiKey: process.env.OPENROUTER_API_KEY || '',
  nvidiaNimApiKey: process.env.NVIDIA_NIM_API_KEY || '',

  supabaseUrl: process.env.SUPABASE_URL || '',
  supabaseKey: process.env.SUPABASE_KEY || '',

  wsMaxConnections: 100,
  sessionTimeoutMs: 30 * 60 * 1000,
  memorySearchLimit: 5,
  memorySimilarityThreshold: 0.7,
};
