FROM node:22-alpine
WORKDIR /app
COPY backend/package*.json ./
RUN npm install --production
COPY backend/ ./
EXPOSE 10000
ENV PORT=10000
CMD ["node", "src/index.js"]
