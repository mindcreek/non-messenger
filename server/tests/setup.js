// Global test setup
process.env.NODE_ENV = 'test';
process.env.PORT = 3001;

// Increase timeout for async operations
jest.setTimeout(10000);

// Mock console.log to reduce noise during tests
const originalLog = console.log;
console.log = (...args) => {
  if (!args[0]?.includes('NonMessenger server running')) {
    originalLog(...args);
  }
};

// Clean up after all tests
afterAll(() => {
  // Restore console.log
  console.log = originalLog;
});
