#!/bin/bash
# JARVIS AI Deployment Script
set -e

echo "=== JARVIS AI Deployment ==="
echo ""

# Check prerequisites
echo "Checking prerequisites..."
command -v node >/dev/null 2>&1 || { echo "Node.js required. Install from https://nodejs.org"; exit 1; }
command -v npm >/dev/null 2>&1 || { echo "npm required."; exit 1; }

# Backend deployment
echo ""
echo "--- Backend Deployment ---"
cd backend

if [ ! -f .env ]; then
    echo "Creating .env from .env.example..."
    cp .env.example .env
    echo "Please edit .env with your API keys before starting."
    exit 1
fi

echo "Installing dependencies..."
npm install --production

echo "Starting backend..."
echo "Backend will run on http://localhost:${PORT:-10000}"
echo "WebSocket on ws://localhost:${PORT:-10000}/ws"
echo ""

# Start in background
node src/index.js &
BACKEND_PID=$!
echo "Backend PID: $BACKEND_PID"
echo $BACKEND_PID > .backend.pid

cd ..

echo ""
echo "=== Deployment Complete ==="
echo ""
echo "Backend: http://localhost:${PORT:-10000}"
echo "Health: http://localhost:${PORT:-10000}/health"
echo "WebSocket: ws://localhost:${PORT:-10000}/ws"
echo ""
echo "To stop: kill \$(cat backend/.backend.pid)"
