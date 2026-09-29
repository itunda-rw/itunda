FROM node:20-alpine
WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev --ignore-scripts
COPY index.js ./
RUN addgroup -S itunda && adduser -S itunda -G itunda
USER itunda
EXPOSE 3000
CMD ["node", "index.js"]
