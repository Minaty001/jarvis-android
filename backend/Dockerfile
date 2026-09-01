FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install --production
COPY . ./
EXPOSE 10000
ENV PORT=10000
CMD ["node", "src/index.js"]
