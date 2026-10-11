import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'

export function NotFoundPage() {
  return (
    <div className="mx-auto mt-10 w-full max-w-sm">
      <Card>
        <CardHeader>
          <CardTitle className="text-2xl">Page not found</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">The page you're looking for doesn't exist.</p>
        </CardContent>
        <CardFooter>
          <Button asChild className="w-full">
            <Link to="/">Back home</Link>
          </Button>
        </CardFooter>
      </Card>
    </div>
  )
}
